/*_____________________________________________________________
Smpl.sc

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
* sort by # channels and sample rate and length range in Smpl
* ability to add tags in gui
* ability to save & load tags and markers in metadata files
* a compact syntax for playing samples inside functions & routines
* similar to say2 for speech. e.g. to be able to be used easily in a composition / scheduling system



/* SMPL TODO

SMPL
TODO: This still needs a lot of work to get functional..
Looping doesn't quite work yet..


I'm in the middle of working on Smpl's playback system..
keep working on that.. I'm making a SampleFileView linked to
SampleFile, right now I have the synthdefs telling me position
and when they are done via SendTrig and OSCfuncs...
the gui part is not done on SampleFile, and once it is,
this gui needs to be integrated with Smpl's gui...
I think that play / stop operations are done ON the SampleFile
and automagically reflected in the respective view... however,
a loop flag must be set manually on the SampleFile

1. Rehash scenes for my new composition concept...
2. make Scenes work more like Macros, Syn, Smpl, & other modules in usage/syntax
3. Allow navigating through scene instances (doesn't seem to work now?)

*/

// TODO: Add a Smpl.find function that returns something similar to what you're looking for, or an array of such


-------------------------------------------------*/



/******************************************************************

@usage

Smpl.lazyLoadGlobal = false;
Smpl.lazyLoadLocal = true;
Smpl.load(verbose: true); // loads global sample library

Smpl.at("drum001"); // get sample by id, or search library by various functions
// e.g. by type, by directory, etc..

Smpl.gui; // open gui editor

*******************************************************************/

Smpl {
  classvar <samples; // all available sample files by id
  classvar <samplesByGroup; // available sample files, by group name
  classvar <allGroups, <allTags; // lists of all available sample groups and tags
  classvar <localSamples;
  classvar <globalSamples;
  classvar <globalSamplesPath; // global samples directory
  classvar <localSamplesPath; // local samples directory

  classvar <activeServer; // server where buffers are allocated and loaded, should be set on load

  // gui variables
  classvar <win, <playLooped=false, <autoPlay=true;
  classvar <currentSample, <sampleFilePlayer, <cursorRoutine, <currentTimePosition=0;

  // boolean toggles lazyloading of samples, false by default. If true will save some ram by only loading samples when necessary
  // However, keep in mind that samples are loaded into buffers on the server asynchronously from other language instructions,
  // and thus you must be wary of performing operations on buffer variables still holding nil.
  classvar <>lazyLoadGlobal=true, <>lazyLoadLocal=false;

  *initClass {
    samples = Dictionary.new;
    samplesByGroup = Dictionary.new;
    globalSamples = Dictionary.new;
    localSamples = Dictionary.new;
    allGroups = List.new;
    allTags = List.new;
    globalSamplesPath = "~/_samples".absolutePath;
  }

  *pr_loadEventTypes {
    // General sample event for buffer playback
    Event.addEventType(\sample, {|s|
      var chans = ~buf.numChannels;
      if(~end.isNil) { ~end = ~buf.numFrames };
      if(~dur == -1) { ~dur = ~buf.duration };
      if(chans == 1) {
        ~instrument = \pitchedSample1ch;
      } {
        ~instrument = \pitchedSample2ch;
      };
      if(~dur.isNil) { ~dur = (~end - ~start) / ~buf.sampleRate };
      ~type = \note;
      currentEnvironment.play;
    }, (freq: \c5.f, rootPitch: \c5.f, atk: 0.01, rel: 0.01, start: 0, out: 0));

    // Playback event for Smpl library
    Event.addEventType(\smpl, {|s|
      ~buf = Smpl.buf(~smpl);
      ~type = \sample;
      currentEnvironment.play;
    }, ());
  }

  // private method
  *pr_checkServerAlive {|errorFunc|
    activeServer = activeServer ? Server.default;
    if(activeServer.serverRunning.not) {
      error("Cannot Allocate Buffers: Boot the Server First!");
      errorFunc.value;
    };
  }

  *samplesForGroup {|gr| ^samplesByGroup.at(gr.asSymbol).values.asList }
  *sampleNamesForGroup {|gr| ^samplesByGroup.at(gr.asSymbol).keys.asList }


  // A global samples directory will be searched for at globalSamplesPath
  // All directories in the array localSamplePaths will be loaded
  // If a server is not provided, server will be the default server.
  *load {|server=nil, verbose=false, limitLocal=nil, limitGlobal=nil, localSamplePaths=nil, doneFunc=nil|
    var samplePath, loaded=0;
    activeServer = server;
    this.pr_checkServerAlive({^nil});
    this.pr_loadEventTypes();

    if(File.exists(globalSamplesPath).not) { File.mkdir(globalSamplesPath) };

    if(localSamplePaths.notNil) { // Load sample directories
      "\nSmpl: Loading Local Sample Banks ...".postln;
      localSamplePaths.do {|samplePath|
        samplePath = PathName.new(samplePath);
        loaded = loaded + this.pr_loadSamples(samplePath, lazyLoadLocal, verbose, limitLocal-loaded);
        ".%.".format(samplePath.folderName).post;
      };
      "\nSmpl: % samples loaded".format(loaded).postln;
    } { "Smpl: No local sample paths provided".postln };


    // Load samples from global directory
    "\nSmpl: Loading Global Samples at %".format(globalSamplesPath).postln;
    samplePath = PathName.new(globalSamplesPath);
    loaded = this.pr_loadSamples(samplePath, lazyLoadGlobal, verbose, limitGlobal);
    "\nSmpl: % samples loaded".format(loaded).postln;

    // Collect groups and tags
    allGroups = Set.new;
    allTags = Set.new;

    samples.do {|sf|
      allGroups.add(sf.library);
      sf.tags.do {|tag| allTags.add(tag) };
    };

    allGroups = allGroups.asList.sort;
    allTags = allTags.asList.sort;
    "\nSmpl: Library loaded % samples".format(samples.size).postln;
    if(doneFunc.notNil) { doneFunc.value() };
  }

  // private method
  *pr_loadSamples {|samplePath, lazyLoad, verbose, limit|
    var res, tmplim = limit;
    res = block {|limitReached|
      samplePath.filesDo {|path,i|
        var res;
        res = this.pr_loadSampleAtPathName(path, lazyLoad, verbose);
        if(res.notNil) {
          if(verbose) {
            "%[%: %]".format(res.name, path.folderName, path.fileName).postln;
          } { if(i%100 == 0) { ".".post } };

          tmplim = tmplim - 1;
          if(tmplim <= 0) { limitReached.value };
        };
      };
    };

    if(res == \limit) {
      "limit % reached, some samples were not loaded".format(limit).warn;
    };

    ^(limit-tmplim);
  }

  *pr_loadSampleAtPathName {|path, lazyLoad, verbose|
    var sf; // samplefile
    var id, group, groupId, preStr = ".";
    var isGlobal = false;

    sf = SampleFile.openRead(path);

    if(sf.notNil) {
      // TEST IF GLOBAL OR LOCAL.. path matches
      // globalSamplesPath
      isGlobal = (path.pathOnly.find(globalSamplesPath) == 0);

      id = path.fileNameWithoutExtension.replace(" ","");
      if(samples.at(id).notNil) {
        if(samples.at(id).path != sf.path) { // if paths are not equal, modify id to avoid doubles
          id = "%_%".format(sf.folderGroups.last.replace(" ","_"), id);
        };
      };

      sf.name = id;
      samples.put(id, sf);
      if(isGlobal) { globalSamples.put(id, sf) } { localSamples.put(id, sf) };

      groupId = path.folderName.asSymbol;
      group = samplesByGroup.at(groupId);
      if(group.isNil) { // new group
        group = Dictionary.new;
        samplesByGroup.put(groupId, group);
      };
      group.put(id, sf);
      sf.library = groupId.asString;

      if(lazyLoad == false) { // if lazyload not active, go ahead and load everything
        sf.loadFileIntoBuffer(activeServer);
        preStr = "...";
      };

      ^sf;
    } {
      if(verbose) { "Smpl: % not found".format(path.fileName).warn };
      ^nil;
    };
  }

  // When lazyloading is active, preload lets you preload groups of samples
  *preload {|samples|
    samples.do {|name|
      var smp = this.samples[name];
      if(smp.isNil) {
        "Sample '%' does not exist, ignored".format(name).error;
      } {
        smp.loadFileIntoBuffer;
      }
    };
  }

  // Returns the SampleFile for a given name
  *at {|name, autoload=true, loadAction|
    var sample, result = nil;
    this.pr_checkServerAlive({^nil});
    sample = this.samples[name];
    if(sample.isNil) {
      "sample '%' not found".format(name).error;
      ^nil
    };
    if(autoload) {
      sample.loadFileIntoBuffer(action:loadAction);
    };
    ^sample;
  }

  // returns the buffer for a given SampleFile id, loads the sample into
  // a buffer if not loaded
  *buf {|name, loadAction|
    ^this.at(name, true, loadAction).buffer;
  }

  *path {|id|
    if(this.samples[id].notNil) {
      ^this.samples[id].path;
    } {
      ^nil;
    }
  }


  *gui {|alwaysOnTop=false|
    var styler, subStyler, decorator, childView, childDecorator, subView;
    var searchText, sampleList, autoPlayCB, txt;
    var searchGroupDropdown, searchTagDropdown;
    var findFunc, resetFunc;
    var width=400, height=800, lineheight=20;
    var sampleListHeight=300, subWinHeight;
    var lastClick = Process.elapsedTime; // used for doubleclick detection
    subWinHeight = height - sampleListHeight - (lineheight*3);

    this.pr_checkServerAlive({^nil});

    // Main Window
    if(win.notNil) { win.close };
    win = Window("Sample Lib", (width+10)@height);
    styler = GUIStyler(win); // initialize with master win
    win.view.decorator = FlowLayout(win.view.bounds);
    win.view.decorator.margin = 0@0;

    // Scrollable Inner View
    childView = styler.getView("SubView", win.view.bounds, scroll: false);
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

    sampleList = ListView(childView, width@sampleListHeight)
    .items_(samples.values.collect({|it| it.name }).asArray.sort)
    .stringColor_(Color.white)
    .background_(Color.clear)
    .hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5));


    // Global options (Autplay, Loop, etc..)
    #autoPlayCB,txt = styler.getCheckBoxAndLabel(childView, "autoplay", lineheight, lineheight, 40, lineheight);
    autoPlayCB.value_(autoPlay).action_({|cb| autoPlay = cb.value });


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
      sampleList.items_(filteredSamples.collect({|it| it.name}).sort);
      resetFunc.();
      subView.removeAll;
      sampleList.value_(0);
    };

    searchText.action = { |st| findFunc.(st.value) };
    searchText.keyUpAction = {|st| st.doAction };
    searchGroupDropdown.action = { findFunc.(searchText.value) };
    searchTagDropdown.action = { findFunc.(searchText.value) };

    // Context-Dependent Sub-window
    subStyler = GUIStyler(childView); // configure substyler...?
    subView = subStyler.getView("Sample Info", Rect(0,0,width,subWinHeight), scroll: true);

    // SINGLE & DOUBLE CLICK ACTION FOR SAMPLE LIST
    sampleList.mouseUpAction_({|sl|
      var now, thresh = 0.3, sample;
      now = Process.elapsedTime;
      if((now - lastClick) < thresh) {
        // Double Click, copy sample name to open document
        var doc = Document.current;
        sample = sl.items[sl.value];
        doc.insertAtCursor("\"%\"".format(sample));
      };
      lastClick = now;

      if(subView.children.size == 0) { // a rare case, when after a search there is no active subview
        sl.action.value(sl);
      }
    });

    // NEW SAMPLEFILE SELECT ACTION
    sampleList.action_({ |sl|
      var sf, sfplayer, id, s_info, btn, txt, check, sfview, insertButton, insertEventBtn, insertArrayBtn, insertPathBtn;
      var playbut, tagfield;
      var playFunc, stopFunc;

      id = sl.items[sl.value];
      sf = samples.at(id);
      "Selected % %".format(id, sf.path).postln;
      // load sample into memory, prep for playback, & create subwindow when done
      sf.loadFileIntoBuffer(activeServer, {|buf|
        if(currentSample.notNil) {currentSample.cleanup};
        currentSample = sf;

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
          tagfield = TextField(subView, (width-40)@lineheight).string_("tags");

          btn = subStyler.getSizableButton(subView, sf.path, size: (width-20)@lineheight);
          btn.action_({|btn| // open in finder
            "open '%'".format(sf.path.dirname).unixCmd;
          });

          // event & array buttons
          insertEventBtn = subStyler.getSizableButton(subView, "event", size: 50@lineheight);
          insertArrayBtn = subStyler.getSizableButton(subView, "array", size: 50@lineheight);
          insertPathBtn = subStyler.getSizableButton(subView, "path", size: 50@lineheight);

          /*
          NEW TIMELINE VIEW (integrated into SampleFile)
          */
          sfview = sf.getWaveformView(subView, width@200);

          // Insert a reference to the samplefile into the IDE
          insertButton.action_({|btn|
            var doc = Document.current;
            doc.insertAtCursor(
              "Smpl.samples.at(\"%\")".format(sf.name)
            );
          });

          // insert a playable event into the IDE
          insertEventBtn.action_({|btn|
            var doc, insertString = "\n%\n".format(sfview.getEventStringForCurrentSelection);
            doc = Document.current;
            doc.insertAtCursor(insertString);
          });

          // insert an array with buffer and selection into the IDE
          insertArrayBtn.action_({|btn|
            var doc, arr = sfview.getArrayValuesForCurrentSelection;
            var insertString = "[\"%\", %, %, %]".format(arr[0], arr[1], arr[2], arr[3]);
            doc = Document.current;
            doc.insertAtCursor(insertString);
          });

          // insert a path to sample file into the IDE
          insertPathBtn.action_({|btn|
            var doc, insertString = sf.path;
            doc = Document.current;
            doc.insertAtCursor(insertString);
          });


          win.onClose = {|win|
            // TODO: clean up everything on window close
            // playnode
            // soundfile
            "CLEANUP SOUNDFILE ETC...".postln;
            sfview.stop;
            sf.stop;
          };

          // Audition the sound file if autoplay is checked
          if(autoPlay) { sfview.play };
        }.fork(AppClock);
      });


    });

    win.front.alwaysOnTop_(alwaysOnTop);
  }

  /*
  Convenience method for sample playback.

  This method can be used in musical applications (contrast against SampleFile's play method
  which should only be used for auditioning).

  The sample is played as a simple synth instance. This method returns a reference to
  the synth instance.
  */
  *splay {|id, start=0, end=(-1), rate=1.0, amp=1.0, out=0, co=20000, rq=1, pan=0|
    var ch, syn, smp = Smpl.samples.at(id);
    ch = smp.numChannels;
    if(end == -1) { end = smp.numFrames };
    if(ch==1) {
      // TODO: why is this less delayed than an event.play? Event needs to be scheduled on a tempo?
      syn = Synth(\pitchedSample1ch, [\amp, amp, \start, start, \end, end, \rootPitch, "A4".f,
        \freq, "A4".f * rate, \atk, 0.001, \rel, 0.1, \co, co, \rq, rq, \out, out, \pan, pan, \buf, smp.buffer]);
    } {
      syn = Synth(\pitchedSample2ch, [\amp, amp, \start, start, \end, end, \rootPitch, "A4".f,
        \freq, "A4".f * rate, \atk, 0.001, \rel, 0.1, \co, co, \rq, rq, \out, out, \pan, pan, \buf, smp.buffer]);
    };
    ^syn;
  }

  // Convenience method, insert the ids/paths of an array of sample ids
  *insertPathnamePairs {|samplelist|
    var rs = "";
    samplelist.do {|sm|
      rs = rs ++ "[\"%\",\"%\"],\n".format(sm, Smpl.at(sm).path);
    };
    rs = "[ \n% \n]".format(rs);
    Document.current.insertAtCursor(rs);
  }

}


/*
SampleFilePlayer {
var <positionBus=nil;
var <playNode=nil;
var <server=nil;
var <sf=nil;
var <sfview=nil;
var <triggerId;



/**** Sample Play Controls ****/
play {|out=0, startframe=0, endframe=(-1), amp=1.0, doneFunc|
var sdef;
this.stop;
if(endframe == -1 || (endframe > sf.buffer.numFrames)) { endframe = sf.buffer.numFrames };
if(sf.buffer.numChannels == 2) { sdef = sdef2ch } { sdef = sdef1ch };
playNode = Synth.new(sdef, [\out, out, \buf, sf.buffer, \pBus, positionBus,
\amp, amp, \start, startframe, \end, endframe]).onFree({ positionBus.setSynchronous(endframe); playNode = nil; if(doneFunc.notNil) { doneFunc.value } });
^this;
}
stop { if(playNode.notNil) { playNode.free; playNode = nil } }
/**** End Sample Play Controls ****/

}

*/


SampleFileView : CompositeView {
  var <sf;
  var <oscfunc; // osc listener for cursor update messages
  var <sfview; // SoundFileView at the core of this view
  // other views
  var <playButton, <loopCheckbox;

  *new {|samplefile, parent, bounds, guistyler|
    ^super.new(parent, bounds).init(samplefile, guistyler);
  }

  // TODO: Use guistyler, if nil create a new one..
  init {|samplefile, guistyler|
    var loopText;
    if(samplefile.isNil) { "Invalid SampleFile provided to SampleFileView: %".format(samplefile).throw };
    sf = samplefile;
    playButton = Button(this, Rect(0, this.bounds.height - 20, 100, 20)).states_([["Play"],["Stop"]]);

    loopCheckbox = CheckBox(this, Rect(120, this.bounds.height-20, 30, 30), "loop");
    loopText = StaticText(this, Rect(160, this.bounds.height-20, 100, 30)).string_("loop").action_({|txt| loopCheckbox.valueAction_(loopCheckbox.value.not) });

    loopCheckbox.action_({|cb|
      sf.loop_(cb.value);
      "looping %".format(cb.value).postln;
    });

    sfview = SoundFileView.new(this, Rect(0,0, this.bounds.width, this.bounds.height - 20)).soundfile_(sf);
    sfview.read(0, sf.numFrames);
    sfview.timeCursorOn_(true).timeCursorColor_(Color.white).timeCursorPosition_(0);
    sfview.mouseUpAction_({|sfv|
      var newpos, clickPos, regionLength;
      #clickPos, regionLength = sfv.selections[sfv.currentSelection];
      "Clicked % %".format(clickPos, regionLength).postln;
    });

    // Start/Stop button Action
    playButton.action_({|btn|
      if(btn.value==1) {// play
        var st, end, len, res;
        #st, len = sfview.selections[sfview.currentSelection];
        if(len == 0) { end = sf.numFrames } { end = st + len };
        if(st < sfview.timeCursorPosition) { st = sfview.timeCursorPosition };

        // 1. enable OSCFunc to recieve cursor position
        if(oscfunc.isNil) {
          oscfunc = OSCFunc.new({|msg|
            var id = msg[2], val = msg[3];
            {
              if(id == sf.triggerId) { // cursor position update
                sfview.timeCursorPosition = val;
              };
              if(id == (sf.triggerId+1)) { // done trigger
                var start, length;
                #start, length = sfview.selections[sfview.currentSelection];
                "done!".postln;
                // if looping is enabled then loop
                "looping is: % %".format(loopCheckbox, loopCheckbox.value).postln;
                if(loopCheckbox.value == true) {
                  // loop
                  "loop damnit".postln;
                  // TODO: Is explicitly resetting the player necessary here?
                  //playNode.set(\start, start, \end, start+length, \loop, 1, \t_reset, 1);
                } { // stop
                  sf.stop();
                  sfview.timeCursorPosition = st;
                  playButton.value_(0);
                };

              };
            }.fork(AppClock);
          }, "/tr");
        };

        // 2. start a playback synth node (free previous if needed)
        //    TODO: ^^^ set a minimum duration limit so as not to overload the system
        res = sf.play(start: st, end: end, out: 0, amp: 1.0);
        if(res.isNil) { sf.stop };

      } {//stop
        sf.stop;
      };
    });

    ^this;
  }

  // Audition current selection
  play {
    playButton.valueAction_(1);
  }

  stop {
    playButton.valueAction_(0);
  }

  currentSelection {
    ^sfview.selections[sfview.currentSelection];
  }

  getEventStringForCurrentSelection {
    var st, len, end;
    #st,len = sfview.selections[sfview.currentSelection];
    if(len==0) { end = -1 } { end = st + len };
    ^sf.eventString(0.5, st, end);
  }

  getArrayValuesForCurrentSelection {
    var st, len, end;
    #st,len = sfview.selections[sfview.currentSelection];
    if(len==0) { end = sf.numFrames } { end = st + len };
    ^[sf.name, sf.numChannels, st, end];
  }

}

// Extension of SoundFile with playback and metadata additions for working within Smpl library
SampleFile : SoundFile {

  // Smpl Plumbing
  classvar rootFolder = "_samples";

  // ** Playback **
  var buffer;      // buffer, if loaded, on the server where this soundfile exists

  // ** Metadata **
  var <>name, <tags, <>library, <folderGroups;  // belongs to a sample library, name of library

  // FUTURE:: fancier analysis-related things
  var frequency_profile;
  var average_tempo;
  var average_pitch;
  var markers; // time markers


  // Playback PLUMBING
  classvar <playdef1ch = \sfPlay1;
  classvar <playdef2ch = \sfPlay2;
  classvar <cursordef1ch = \SampleFileCursorPos1Channel;
  classvar <cursordef2ch = \SampleFileCursorPos2Channel;

  var <playNode;    // synth node controlling buffer playback
  var <loop = false; // boolean: loop playback


  // GUI plumbing
  classvar <nextTriggerId=0;
  var <triggerId;

  // GUI views
  var waveformview=nil; // main composite view

  cleanup {
    this.stop;
    waveformview = nil;
  }

  /*
  @Override
  @path A PathName
  NOTE: This needs to be redone to return an instance of SampleFile, I might actually need to rewrite SoundFile
  */
  *openRead {|path| if(this.isSoundFile(path)) { ^super.new.init(path) } { ^nil } }

  // path is of type PathName
  init {|path|
    triggerId = nextTriggerId;
    nextTriggerId = nextTriggerId + 2;

    this.openRead(path.asAbsolutePath); // get metadata
    this.close; // close file resource
    tags = Set.new;
    folderGroups = this.pr_getFolderGroups(path);
    // TODO: Should load tags and other info from external metadata file here
    name = path.fileNameWithoutExtension.replace(" ","");

  }

  // path is of type PathName
  pr_getFolderGroups {|path|
    var dirnames, rootpos;
    dirnames = path.pathOnly.split($/);
    rootpos = dirnames.find([rootFolder]);
    if(rootpos.isNil) {
      ^nil;
    } {
      ^dirnames[(rootpos+1)..]; // everything after the root directory
    };

  }


  // Get a waveform view attached to this samplefile
  getWaveformView {|parent, bounds|
    var loopText;
    if(waveformview.notNil) { ^waveformview };
    if(bounds.isKindOf(Point)) { bounds = Rect(0,0,bounds.x,bounds.y) };
    waveformview = SampleFileView.new(this, parent, bounds);
    ^waveformview;
  }


  /*
  Play method ONLY FOR AUDITIONING or in tandem with SampleFileView

  This method should not be used in patterns or other timed musical applications.
  This method keeps only a single synth instance active and resets the playback position
  as needed. It works in tandem with SampleFileView to maintain cursor position and looping behavior.

  For musical applications use the `event` method to get an independent playable event.
  */
  play {|server=nil, start=0, end=(-1), out=0, amp=1.0|
    var syn = if(numChannels == 2) { cursordef2ch } { cursordef1ch };
    if(server.isNil) { server = Server.default };
    this.pr_checkServerAlive(server, {^nil});
    this.pr_checkBufferLoaded({^nil});
    if(end == -1) { end = this.numFrames };
    if(end-start > 1000) {
      if(playNode.notNil) { playNode.free; playNode = nil };
      playNode = Synth(syn, [\buf, buffer, \out, out, \amp, amp, \start, start, \end, end, \tid, triggerId, \loop, loop]);
      ^this;
    } {
      ^nil;
    };
  }

  stop {
    if(playNode.notNil) { playNode.free; playNode = nil };
  }

  loop_ {|bool=false|
    loop = bool;
    if(playNode.notNil) { playNode.set(\loop, loop.asInt) };
  }


  *loadSynthDefs {
    if(SynthDescLib.global.synthDescs.at(playdef1ch).isNil) {
      SynthDef(playdef1ch, {|amp, out, start, end, buf|
        var sig, head;
        head = Line.ar(start, end, ((end-start) / (SampleRate.ir * BufRateScale.kr(buf))), doneAction: 2);
        sig = BufRd.ar(1, buf, head, 0);
        Out.ar(out, sig * amp);
      }).add;
    };

    if(SynthDescLib.global.synthDescs.at(playdef2ch).isNil) {
      SynthDef(playdef2ch, {|amp, out, start, end, buf|
        var sig, head;
        head = Line.ar(start, end, ((end-start) / (SampleRate.ir * BufRateScale.kr(buf))), doneAction: 2);
        sig = BufRd.ar(2, buf, head, 0);
        Out.ar(out, sig * amp);
      }).add;
    };

    if(SynthDescLib.global.synthDescs.at(cursordef1ch).isNil) {
      SynthDef(cursordef1ch, {|amp=0.5, out=0, tid, start=0, end, loop=1, buf, cursorRate=20|
        var sig, pos, dur, env, t_done, reset;
        var controlBlockFrames = SampleRate.ir / ControlRate.ir;
        reset = \t_reset.tr(1);
        dur = (end-start) / (SampleRate.ir * BufRateScale.kr(buf));
        pos = Phasor.ar(reset, 1 * BufRateScale.kr(buf), start, end, start);
        sig = BufRd.ar(1, buf, pos, 0);
        t_done = T2K.kr(pos >= (end-controlBlockFrames));
        SendTrig.kr(Impulse.kr(cursorRate) * (1 - t_done), tid, pos); // cursor position
        SendTrig.kr(t_done, tid+1, 1.0); // done signal
        FreeSelf.kr(t_done * (1-loop));
        Out.ar(out, sig * amp);
      }).add;
    };

    if(SynthDescLib.global.synthDescs.at(cursordef2ch).isNil) {
      SynthDef(cursordef2ch, {|amp=0.5, out=0, tid, start=0, end, loop=1, buf, cursorRate=20|
        var sig, pos, dur, env, t_done, reset;
        var controlBlockFrames = SampleRate.ir / ControlRate.ir;
        reset = \t_reset.tr(1);
        dur = (end-start) / (SampleRate.ir * BufRateScale.kr(buf));
        pos = Phasor.ar(reset, 1 * BufRateScale.kr(buf), start, end, start);
        sig = BufRd.ar(2, buf, pos, 0);
        t_done = T2K.kr(pos >= (end-controlBlockFrames));
        SendTrig.kr(Impulse.kr(cursorRate) * (1 - t_done), tid, pos); // cursor position
        SendTrig.kr(t_done, tid+1, 1.0); // done signal
        FreeSelf.kr(t_done * (1-loop));
        Out.ar(out, sig * amp);
      }).add;
    };

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

  buffer { this.pr_checkBufferLoaded({^nil}); ^buffer }


  addTag {|tag| tags.add(tag) }
  hasTag {|tag| if(tag.isNil) { ^true } { ^tags.contains(tag) } }

  *isSoundFile {|path|
    if(path.class != PathName) { path = PathName.new(path) };
    ^"^(wav[e]?|aif[f]?)$".matchRegexp(path.extension.toLower);
  }

  /*
  Return a playback event that can be used in musical applications.
  */
  event {|amp=0.5, startframe=0, endframe=(-1)|
    var sdef;
    this.pr_checkBufferLoaded({^nil});
    sdef = if(this.numChannels == 2) { playdef2ch } { playdef1ch };
    if(endframe == -1) { endframe = this.numFrames };
    ^(instrument: sdef, amp: amp, start: startframe, end: endframe, buf: buffer.bufnum);
  }

  eventString {|amp=0.5, startframe=0, endframe=(-1)|
    var sdef;
    this.pr_checkBufferLoaded({^nil});
    sdef = if(this.numChannels == 2) { playdef2ch } { playdef1ch };
    if(endframe == -1) { endframe = this.numFrames };
    ^"(instrument: '%', amp: %, start: %, end: %, buf: Smpl.samples.at(\"%\").buffer)".format(sdef, amp, startframe, endframe, this.name);
  }

  /*
  @param server The server to load this buffer into
  @param action Function to be evaluated once the file has been loaded into the server. Function is passed the buffer as an argument.
  */
  loadFileIntoBuffer {|server, action=nil|
    var newbuf, newaction;
    server = server ? Server.default;
    this.pr_checkServerAlive(server, { ^nil });
    SampleFile.loadSynthDefs;
    newaction = action;
    if(buffer.notNil) { newaction.value(buffer) }
    { // allocate new buffer
      newbuf = Buffer.read(server, this.path, action: newaction);
      buffer = newbuf;
    };
    ^this;
  }

  // Convenience method for loadFileIntoBuffer
  load {|server, action| this.loadFileIntoBuffer(server, action) }

  isLoaded {|server|
    server = server ? Server.default;
    this.pr_checkServerAlive(server, {^false});
    ^buffer.notNil
  }

  bufnum { ^buffer.bufnum }
  asString { ^("SampleFile"+path) }

}



