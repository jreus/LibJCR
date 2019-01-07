/*_____________________________________________________________
SampleLib.sc

Sample library manager and buffer utilities

(C) 2018 Jonathan Reus / GPLv3
http://jonathanreus.com

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see http://www.gnu.org/licenses/

________________________________________________________________*/


/*-------------------------------------------------
Features / Todos
* selectable playback region option in gui
* ability to add markers in gui
* ability to add tags in gui
* ability to save & load tags and markers in metadata files
* a compact syntax for playing samples inside functions & routines
* similar to say2 for speech. e.g. to be able to be used easily in a composition / scheduling system


-------------------------------------------------*/



/******************************************************************

@usage
SampleLib

SampleLib.lazyLoadGlobal = false;
SampleLib.lazyLoadLocal = true;
SampleLib.load(verbose: true); // loads global & local sample libraries

SampleLib.at("drum001"); // get sample by id, or search library by various functions
// e.g. by type, by directory, etc..

SampleLib.gui; // open gui editor

*******************************************************************/

SampleLib {
  classvar <samples; // all available sample files by id
  classvar <samplesByGroup; // available sample files, by group name
  classvar <allGroups, <allTags; // lists of all available sample groups and tags
  classvar <globalSamplesPath; // global samples directory
  classvar <localSamplesPath; // local samples directory

  classvar <activeServer; // server where buffers are allocated and loaded, should be set on load

  // gui variables
  classvar <win, <playLooped=false, <playOnSelect=true;
  classvar <currentSample, <sampleFilePlayer, <cursorRoutine, <currentTimePosition=0;

  // boolean toggles lazyloading of samples, false by default. If true will save some ram by only loading samples when necessary
  // However, keep in mind that samples are loaded into buffers on the server asynchronously from other language instructions,
  // and thus you must be wary of performing operations on buffer variables still holding nil.
  classvar <>lazyLoadGlobal=true, <>lazyLoadLocal=false;

  *initClass {
    samples = Dictionary.new;
    samplesByGroup = Dictionary.new;
    allGroups = List.new;
    allTags = List.new;
    globalSamplesPath = "~/_samples".absolutePath;
  }


  // private method
  *pr_checkServerAlive {|errorFunc|
    activeServer = activeServer ? Server.default;
    if(activeServer.serverRunning.not) {
      error("Cannot Allocate Buffers: Boot the Server First!");
      errorFunc.value;
    };
  }

  // By default will look for a local '_samples' directory in the same directory as the current document.
  // A global samples directory will be searched for at globalSamplesPath
  // If a server is not provided, server will be the default server.
  *load {|server=nil,localsampledir=nil,verbose=false,limit=nil|
    var samplePath;
    activeServer = server;
    this.pr_checkServerAlive({^nil});

    if(localsampledir.isNil) { localsampledir = Document.current.dir +/+ "_samples" };
    localSamplesPath = localsampledir;
    if(File.exists(localSamplesPath).not) { File.mkdir(localSamplesPath) };
    if(File.exists(globalSamplesPath).not) { File.mkdir(globalSamplesPath) };

    // Load samples from global & local paths
    "\n... Loading Global Samples ... %".format(globalSamplesPath).postln;
    samplePath = PathName.new(globalSamplesPath);
    this.pr_loadSamples(samplePath, lazyLoadGlobal, verbose, limit);
    "\n... Loading Local Samples ... %".format(localSamplesPath).postln;
    samplePath = PathName.new(localSamplesPath);
    this.pr_loadSamples(samplePath, lazyLoadLocal, verbose, limit);

    // Collect groups and tags
    allGroups = Set.new;
    allTags = Set.new;

    samples.do {|sf|
      allGroups.add(sf.library);
      sf.tags.do {|tag| allTags.add(tag) };
    };

    allGroups = allGroups.asList.sort;
    allTags = allTags.asList.sort;
    "\n... Finished Loading % Samples ...".format(samples.size).postln;
  }

  // private method
  *pr_loadSamples {|samplePath, lazyLoad, verbose, limit|
    var res;
    res = block {|break|
      samplePath.filesDo {|path,i|
        var md; // metadata
        var id, group, groupId, preStr = ".";

        md = SampleFile.openRead(path);

        if(md.notNil) {
          //[md.duration, md.headerFormat, md.sampleFormat, md.numFrames, md.numChannels, md.sampleRate, path.folderName, path.extension, path.fileName, md.path].postln;

          id = path.fileNameWithoutExtension.replace(" ","");
          block {|duplicate|
           while {samples.at(id).notNil} {
              if(samples.at(id).path == md.path) { // this exact sample already exists
                duplicate.value;
              };
              id = id ++ "*"; // otherwise add asterixes until a free id is found
            };
          };
          if(limit.notNil.and({samples.at(id).isNil}).and {samples.size >= limit}) { break.value(\limit) };
          md.name = id;
          samples.put(id, md);
          groupId = path.folderName.asSymbol;
          group = samplesByGroup.at(groupId);
          if(group.isNil) { // new group
            group = Dictionary.new;
            samplesByGroup.put(groupId, group);
          };
          group.put(id, md);
          md.library = groupId.asString;

          if(lazyLoad == false) { // if lazyload not active, go ahead and load everything
            md.loadFileIntoBuffer(activeServer);
            preStr = "...";
          };

          if(verbose)
            { "%[%: %]".format(preStr, path.folderName, path.fileName).postln }
            { if(i%100 == 0) { ".".post } };

        } {
          if(verbose) { path.fileName.warn };
        };
      };
    };
    "".postln;
    if(res == \limit) {
      "Sample limit % reached, some samples were not loaded".format(limit).warn;
    }
  }

  // When lazyloading is active, preload lets you preload groups of samples
  *preload {
    /*** TODO: STUB ***/
  }

  // Returns the SampleFile for a given id
  *at {|id|
    var sample, result = nil;
    this.pr_checkServerAlive({^nil});
    sample = this.samples[id];
    if(sample.isNil) {
      "sample '%' not found".format(id).error;
      ^nil
    };
    ^sample;
  }


  *gui {
    var styler, subStyler, decorator, childView, childDecorator, subView;
    var searchText, searchList, findFunc, resetFunc;
    var searchGroupDropdown, searchTagDropdown;
    var width=400, height=800, lineheight=20, searchListHeight=300, subWinHeight;
    subWinHeight = height - searchListHeight - (lineheight*3);

    this.pr_checkServerAlive({^nil});

    // Main Window
    if(win.notNil) { win.close };
    win = Window("Sample Lib", (width+10)@height);
    styler = GUIStyler(win); // initialize with master win
    win.view.decorator = FlowLayout(win.view.bounds);
    win.view.decorator.margin = 0@0;

    // Scrollable Inner View
    childView = styler.getWindow("SubView", win.view.bounds, scroll: false);
    childView.decorator = FlowLayout(childView.bounds);
    childDecorator = decorator;


    // Search by Name & Category
    searchText = TextField(childView, width@lineheight);

    searchGroupDropdown = PopUpMenu(childView, (width/2.1)@lineheight)
    .items_(allGroups.asArray.insert(0, "--")).stringColor_(Color.white)
    .background_(Color.clear);

    searchTagDropdown = PopUpMenu(childView, (width/2.1)@lineheight)
    .items_(allTags.asArray.insert(0, "--")).stringColor_(Color.white)
    .background_(Color.clear);

    searchList = ListView(childView, width@searchListHeight)
    .items_(samples.values.collect({|it| it.name }).asArray.sort)
    .stringColor_(Color.white)
    .background_(Color.clear)
    .hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5));

    // clear playing samples & cursor routine
    resetFunc = {
      sampleFilePlayer.stop;
      cursorRoutine.stop;
    };


    findFunc = { |name|
      var group=nil, tag=nil, filteredSamples=nil;
      if(searchGroupDropdown.value > 0) {
        group = searchGroupDropdown.item;
      };
      if(searchTagDropdown.value > 0) {
        tag = searchTagDropdown.item;
      };
      filteredSamples = samples.values.selectAs({|sf, i|
        var crit1, crit2, crit3;
        crit1 = if(group.isNil) { true } { sf.library == group };
        crit2 = sf.hasTag(tag);
        if(name=="") {
          crit3 = true;
        } {
          crit3 = sf.name.containsi(name);
        };
        crit1 && crit2 && crit3;
      }, Array);
      searchList.items_(filteredSamples.collect({|it| it.name}).sort);
      resetFunc.();
      subView.removeAll;
      searchList.value_(0);
    };

    searchText.action = { |st| findFunc.(st.value) };
    searchText.keyUpAction = {|st| st.doAction };
    searchGroupDropdown.action = { findFunc.(searchText.value) };
    searchTagDropdown.action = { findFunc.(searchText.value) };

    // Context-Dependent Sub-window
    subStyler = GUIStyler(childView); // configure substyler...?
    subView = subStyler.getWindow("Sample Info", width@subWinHeight, scroll: true);

    searchList.mouseUpAction_({|sl|
      if(subView.children.size == 0) { // a rare case, when after a search there is no active subview
        sl.action.value(sl);
      }
    });

    searchList.action_({ |sl| // action when selecting items in the search list
      var sf, sfplayer, id, s_info, btn, txt, check, sfview, insertButton;
      var playbut, tagfield;
      var playFunc, stopFunc;

      id = sl.items[sl.value];
      sf = samples.at(id);
      "Selected % %".format(id, sf.path).postln;
      // load sample into memory, prep for playback, & create subwindow when done
      sf.loadFileIntoBuffer(activeServer, {|buf|
        sfplayer = sf.getSamplePlayer(activeServer);
        resetFunc.();
        currentSample = sf;
        sampleFilePlayer = sfplayer;

        // everything else happens on appclock
        {
          // Build the Sample Info window
          subView.removeAll; // remove all views from the sub window
          subView.decorator = FlowLayout(subView.bounds);
          insertButton = subStyler.getSizableButton(subView, sf.name, size: (width-20-60-40-60-10)@lineheight); // name

          subStyler.getSizableText(subView, "%ch".format(sf.numChannels), 20); // channels
          subStyler.getSizableText(subView, sf.sampleRate, 40); // sample rate
          subStyler.getSizableText(subView, sf.headerFormat, 40); // format
          subStyler.getSizableText(subView, sf.duration.round(0.01).asString ++ "s", 60); // length


          subStyler.getHorizontalSpacer(subView, width);

          // Tags
          txt = subStyler.getSizableText(subView, "tags", 30);
          tagfield = TextField(subView, (width-40)@lineheight);

          btn = subStyler.getSizableButton(subView, sf.path, size: (width-100)@lineheight);
          btn.action = {|btn| "open %".format(sf.path.dirname).unixCmd }; // browse in finder


          // Timeline view
          sfview = SoundFileView.new(subView, width@200).soundfile_(sf);
          sfview.read(0, sf.numFrames);
          sfview.timeCursorOn_(true).timeCursorColor_(Color.white).timeCursorPosition_(0);

          // Insert play function into IDE action
          insertButton.action_({|btn|
            var st, len, end, doc;
            #st,len = sfview.selections[sfview.currentSelection];
            if(len==0) { end = -1 } { end = st + len };
            doc = Document.current;
            "Current: % %".format(doc.selectionStart, doc.title).postln;
            doc.insertAtCursor(
              "SampleLib.samples.at(\"%\").play(s, 0, %, %, 1.0)"
              .format(sf.name, st, end)
            )
          });

          // Transport & Playback Controls
          playbut = subStyler.getSizableButton(subView, "Play", "Stop", 100@lineheight);

          check = subStyler.getCheckBox(subView, "loop", lineheight, lineheight);
          check.action_({|cb| if(cb.value == true) { playLooped=true } { playLooped=false } })
          .value_(playLooped);
          subStyler.getSizableText(subView, "loop", 20, \left, 10);

          check = subStyler.getCheckBox(subView, "playonselect", lineheight, lineheight);
          check.action_({|cb|
            if(cb.value == true) { playOnSelect=true } { playOnSelect=false }
          }).value_(playOnSelect);
          subStyler.getSizableText(subView, "autoplay", 20, \left, 10);

          playFunc = {|out=0,amp=1.0|
            var st, end, len, loopst, pos;
            #st, len = sfview.selections[sfview.currentSelection];
            loopst = st;
            if(len == 0) { end = sf.numFrames; loopst=0 } { end = st + len };
            pos = currentTimePosition;
            if((pos >= end).or {pos < st}) { pos = st };
            sfplayer.positionBus.setSynchronous(pos);
            sfplayer.play(out, pos, end, amp);
            cursorRoutine = {
              var cpos = pos;
              loop {
                sfview.timeCursorPosition = cpos;
                currentTimePosition = cpos;
                if(cpos >= end) {
                  if(playLooped)
                  { sfplayer.play(out, loopst, end, amp) }
                  { playbut.valueAction_(0) };
                };
                0.01.wait;
                cpos = sfplayer.positionBus.getSynchronous;
            }}.fork(AppClock);
          };

          stopFunc = { sfplayer.stop; cursorRoutine.stop };

          playbut.action_({ |btn|
            if(btn.value == 1) { playFunc.() } { stopFunc.(); btn.refresh };
          });


          sfview.mouseUpAction_({|sfv|
            var newpos, isPlaying, clickPos, regionLen;
            isPlaying = sfplayer.isPlaying;
            #clickPos, regionLen = sfv.selections[sfv.currentSelection];
            "New Click % %".format(clickPos, regionLen).postln;
            if(isPlaying) { // stop & play at new position
              stopFunc.();
              currentTimePosition = clickPos;
              playFunc.();
            } { // not playing, so just update the position
              currentTimePosition = clickPos;
              sfplayer.positionBus.setSynchronous(clickPos);
            };
          });

          win.onClose = {|win|
            sampleFilePlayer.stop;
            sampleFilePlayer = nil;
            currentSample.close;
            currentSample = nil;
            cursorRoutine.stop;
            cursorRoutine=nil;
          };

        if(playOnSelect) { currentTimePosition=0; playbut.valueAction_(1) };
      }.fork(AppClock);
    });


  });

  win.front;
}

}


SampleFilePlayer {
  var <positionBus=nil;
  var <playNode=nil;
  var <server=nil;
  var <sFile=nil;

  classvar <def1ch = \SampleFilePlayNode1Channel;
  classvar <def2ch = \SampleFilePlayNode2Channel;


  *new {|sf, serv|
    ^super.new.init(sf, serv);
  }

  init {|sf, serv|
    server = serv ? Server.default;
    sFile = sf;
    if(positionBus.isNil) {
      positionBus = Bus.new(\control, numChannels: 1, server: server);
    };
  }


  *loadSynthDefs {
    if(SynthDescLib.global.synthDescs.at(def1ch).isNil) {
      SynthDef(def1ch, {|amp, out, start, end, buf, pBus|
        var sig, head;
        head = Line.ar(start, end, ((end-start) / (SampleRate.ir * BufRateScale.kr(buf))), doneAction: 2);
        sig = BufRd.ar(1, buf, head, 0);
        Out.kr(pBus, A2K.kr(head));
        Out.ar(out, sig * amp);
      }).add;
    };

    if(SynthDescLib.global.synthDescs.at(def2ch).isNil) {
      SynthDef(def2ch, {|amp, out, start, end, buf, pBus|
        var sig, head;
        head = Line.ar(start, end, ((end-start) / (SampleRate.ir * BufRateScale.kr(buf))), doneAction: 2);
        sig = BufRd.ar(2, buf, head, 0);
        Out.kr(pBus, A2K.kr(head));
        Out.ar(out, sig * amp);
      }).add;
    };
  }

  /**** Sample Play Controls ****/
  isPlaying { ^playNode.notNil }

  play {|out=0, startframe=0, endframe=(-1), amp=1.0, doneFunc|
    var sdef;
    this.stop;
    if(endframe == -1 || (endframe > sFile.buffer.numFrames)) { endframe = sFile.buffer.numFrames };
    if(sFile.buffer.numChannels == 2) { sdef = def2ch } { sdef = def1ch };
    playNode = Synth.new(sdef, [\out, out, \buf, sFile.buffer, \pBus, positionBus,
      \amp, amp, \start, startframe, \end, endframe]).onFree({ positionBus.setSynchronous(endframe); playNode = nil; if(doneFunc.notNil) { doneFunc.value } });
    ^this;
  }
  stop { if(playNode.notNil) { playNode.free; playNode = nil } }
  /**** End Sample Play Controls ****/

}

SampleFile : SoundFile {

  // ** Playback **
  var <buffer;      // buffer, if loaded, on the server where this soundfile exists
  var <playNode;    // synth node controlling buffer playback

  // ** Metadata **
  var <>name, <tags, <>library;  // belongs to a sample library, name of library

  // FUTURE:: fancier analysis-related things
  var frequency_profile;
  var average_tempo;
  var average_pitch;
  var markers; // time markers

  /*
  @Override
  @path A PathName
  NOTE: This needs to be redone to return an instance of SampleFile, I might actually need to rewrite SoundFile
  */
  *openRead {|path| if(this.isSoundFile(path)) { ^super.new.init(path) } { ^nil } }

  init {|path|
    this.openRead(path.asAbsolutePath); // get metadata
    this.close; // close file resource
    tags = Set.new;
    // TODO: Should load tags and other info from external metadata file here
    name = path.fileNameWithoutExtension.replace(" ","");

  }

  // utility function used when an active server is needed
  pr_checkServerAlive {|server, errorFunc|
    if(server.serverRunning.not) {
      error("Cannot complete operation: Boot the Server First!");
      errorFunc.value;
    };
  }
  pr_checkBufferLoaded {|errorFunc|
    if(buffer.isNil) {
      error("Cannot complete operation: Load buffer into memory first!");
      errorFunc.value;
    };
  }


  addTag {|tag| tags.add(tag) }
  hasTag {|tag| if(tag.isNil) { ^true } { ^tags.contains(tag) } }

  *isSoundFile {|path|
    if(path.class != PathName) { path = PathName.new(path) };
    ^"^(wav[e]?|aif[f]?|raw)$".matchRegexp(path.extension.toLower);
  }

  /*
  @param server The server to load this buffer into
  @param action Function to be evaluated once the file has been loaded into the server. Function is passed the buffer as an argument.
  */
  loadFileIntoBuffer {|server, action=nil|
    var newbuf, newaction;
    server = server ? Server.default;
    this.pr_checkServerAlive(server, { ^nil });
    SampleFilePlayer.loadSynthDefs;
    newaction = action;
    if(buffer.notNil) { newaction.value(buffer) }
    { // allocate new buffer
      newbuf = Buffer.read(server, this.path, action: newaction);
      buffer = newbuf;
    };
    ^this;
  }

  // Convenience method for loadFileIntoBuffer
  load {|server, prepForPlayback=true, action| this.loadFileIntoBuffer(server, prepForPlayback, action) }

  isLoaded {|server|
    server = server ? Server.default;
    this.pr_checkServerAlive(server, {^false});
    ^buffer.notNil
  }

  bufnum { ^buffer.bufnum }
  asString { ^("SampleFile"+path) }

  getSamplePlayer {|server|
    this.pr_checkServerAlive(server, { ^nil });
    this.pr_checkBufferLoaded({^nil});
    ^SampleFilePlayer.new(this, server)
  }

  loadAndPlay {|server, out=0, startframe=0, endframe=(-1), amp=1.0|
    this.loadFileIntoBuffer(server, true,
      {|buf|
        ^this.getSamplePlayer(server).play(out,startframe,endframe,amp)
    });
  }

  play {|server, out=0, startframe=0, endframe=(-1), amp=1.0|
    ^this.getSamplePlayer(server).play(out, startframe, endframe, amp);
  }



}



