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
	classvar <win, <currentSample, <an_cursor, <currentTimePosition=0;
  classvar <playLooped=false, <playOnSelect=true;

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
	*load {|server=nil,localsampledir=nil,verbose=false|
		var samplePath;
		activeServer = server;
		this.pr_checkServerAlive({^nil});

		if(localsampledir.isNil) { localsampledir = Document.current.dir +/+ "_samples" };
		localSamplesPath = localsampledir;
		if(File.exists(localSamplesPath).not) { File.mkdir(localSamplesPath) };
		if(File.exists(globalSamplesPath).not) { File.mkdir(globalSamplesPath) };

		// Load samples from global & local paths
		if(verbose) { "... Loading Global Samples ... %".format(globalSamplesPath).postln };
		samplePath = PathName.new(globalSamplesPath);
		this.pr_loadSamples(samplePath, lazyLoadGlobal, verbose);
		if(verbose) { "... Loading Local Samples ... %".format(localSamplesPath).postln };
		samplePath = PathName.new(localSamplesPath);
		this.pr_loadSamples(samplePath, lazyLoadLocal, verbose);

		// Collect groups and tags
		allGroups = Set.new;
		allTags = Set.new;

		samples.do {|sf|
			allGroups.add(sf.library);
			sf.tags.do {|tag| allTags.add(tag) };
		};

		allGroups = allGroups.asList.sort;
		allTags = allTags.asList.sort;
    "... Finished Loading Samples ...".postln;
	}

	// private method
	*pr_loadSamples {|samplePath, lazyLoad, verbose|
		samplePath.filesDo {arg path;
			var md; // metadata
			var id, group, groupId, preStr = ".";
			md = SampleFile.openRead(path);

			if(md.notNil) {
				//[md.duration, md.headerFormat, md.sampleFormat, md.numFrames, md.numChannels, md.sampleRate, path.folderName, path.extension, path.fileName, md.path].postln;

				id = path.fileNameWithoutExtension.replace(" ","");
				while {samples.at(id).notNil} { id = id ++ "*" };
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

				if(verbose) { "%[%: %]".format(preStr, path.folderName, path.fileName).postln };

			} {
				if(verbose) { path.fileName.warn };
			};
		};
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
		var searchText, searchList, findFunc;
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
		};

		searchText.action = { |field| findFunc.(field.value) };
		searchText.keyUpAction = {|view| view.doAction };
		searchGroupDropdown.action = { findFunc.(searchText.value) };
		searchTagDropdown.action = { findFunc.(searchText.value) };

		// Context-Dependent Sub-window
		subStyler = GUIStyler(childView); // configure substyler...?
		subView = subStyler.getWindow("Sample Info", width@subWinHeight, scroll: true);

		searchList.action_({ |sl| // action when selecting items in the search list
			var sf, id, s_info, btn, txt, check, sfview;
			var playbut;
			var playFunc, stopFunc;

			if(currentSample.notNil) { // clean up previously displayed SampleFile
				subView.removeAll; // remove all views from the sub window
				currentSample.stop;
				an_cursor.stop;
        an_cursor=nil;
			};

			id = sl.items[sl.value];
			sf = samples.at(id);
			"Selected % %".format(id, sf.path).postln;
			// load sample into memory, prep for playback, & create subwindow when done
			sf.loadFileIntoBuffer(activeServer, true, {|buf|

        currentSample = sf;

				// everything else is gui-building and happens happens on appclock
				{
					// Build the Sample Info window
					subView.decorator = FlowLayout(subView.bounds);
					btn = subStyler.getSizableButton(subView, sf.name, side: 100@lineheight); // name

          btn.action_({|btn| Document.current.insertAtCursor("snd/%".format(sf.name)) });

          subStyler.getSizableText(subView, "%ch".format(sf.numChannels), 40); // channels
          subStyler.getSizableText(subView, sf.sampleRate, 80); // sample rate
					subStyler.getSizableText(subView, sf.headerFormat, 40); // format



          // Tags
					txt = TextField(subView, (width-20)@lineheight).string_("tags");

					btn = subStyler.getSizableButton(subView, sf.path, size: (width-100)@lineheight);
					btn.action = {|btn| "open %".format(sf.path.dirname).unixCmd }; // browse in finder


					// Timeline view
					sfview = SoundFileView.new(subView, width@200).soundfile_(sf);
					sfview.read(0, sf.numFrames);
					sfview.timeCursorOn_(true).timeCursorColor_(Color.white).timeCursorPosition_(0);

					// Transport & Playback Controls
					playbut = subStyler.getSizableButton(subView, "Play", "Stop", 100@lineheight);
					an_cursor = nil;

					check = subStyler.getCheckBox(subView, "loop", lineheight, lineheight);
					check.action_({|cb| if(cb.value == true) { playLooped=true } { playLooped=false } })
					.value_(playLooped);
					subStyler.getSizableText(subView, "loop", 20, \left, 10);

					check = subStyler.getCheckBox(subView, "playonselect", lineheight, lineheight);
					check.action_({|cb|
						if(cb.value == true) { playOnSelect=true } { playOnSelect=false }
					}).value_(playOnSelect);
					subStyler.getSizableText(subView, "autoplay", 20, \left, 10);

					playFunc = {
						var spos = currentTimePosition;
						if(spos >= sf.numFrames) { spos = 0 };
            "playfunc % %".format(spos, -1).postln;
						sf.play(activeServer, 0, spos, -1, 1.0);
						an_cursor = {
							var cpos = spos;
							loop {
								sfview.timeCursorPosition = cpos;
                currentTimePosition = cpos;
								if(cpos == sf.numFrames) {
									if(playLooped) { sf.play(activeServer, 0, 0, -1) }
									{ playbut.valueAction_(0) };
								};
								0.05.wait;
								cpos = sf.positionBus.getSynchronous;
						}}.fork(AppClock);
					};

					stopFunc = {
						sf.stop; an_cursor.stop; an_cursor=nil;
					};

					playbut.action_({ |btn|
						if(btn.value == 1) { playFunc.() } { stopFunc.() };
					});

          sfview.action_({|sfv|
            var newpos, isPlaying = sf.isPlaying;
            newpos = sfv.timeCursorPosition;
            if(newpos != currentTimePosition) {
              currentTimePosition = newpos;
              "Moveto %".format(currentTimePosition).postln;
              if(isPlaying) {
                // stop everything & play at the new position
                stopFunc.();
                playFunc.();
              } { // not playing, so just update the position bus
                sf.positionBus.setSynchronous(newpos);
              };
            };
          });

					if(playOnSelect) { currentTimePosition=0; playbut.valueAction_(1) };
				}.fork(AppClock);
			});


		});
		win.front;
	}

}


SampleFile : SoundFile {

	// ** Playback **
	var <buffer;      // buffer, if loaded, on the server where this soundfile exists
	var <positionBus; // control bus with current playback frame
	var <playNode;    // synth node controlling buffer playback
	classvar <def1ch = \SampleFilePlayNode1Channel;
	classvar <def2ch = \SampleFilePlayNode2Channel;


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
		this.openRead(path.asAbsolutePath);
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
	loadFileIntoBuffer {|server, prepForPlayback=true, action=nil|
		var newbuf, newaction;
		server = server ? Server.default;
		this.pr_checkServerAlive(server, { ^nil });
		if(prepForPlayback == true) { this.prepPlayback(server) };
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

	bufnum { ^buffer.bufnum }
  asString { ^("SampleFile"+path) }

	pr_loadSynthDefs {
		if(SynthDescLib.global.synthDescs.at(def1ch).isNil) {
			SynthDef(def1ch, {|amp, out, start, end, buf, pBus|
				var sig, head;
        head = Line.ar(start, end, ((end-start) / (SampleRate.ir * BufRateScale.kr(buf))));
				sig = BufRd.ar(1, buf, head, 0);
				Out.kr(pBus, A2K.kr(head));
				Out.ar(out, sig);
			}).add;
		};

		if(SynthDescLib.global.synthDescs.at(def2ch).isNil) {
			SynthDef(def2ch, {|amp, out, start, end, buf, pBus|
				var sig, head;
				head = Line.ar(start, end, ((end-start) / (SampleRate.ir * BufRateScale.kr(buf))));
				sig = BufRd.ar(2, buf, head, 0);
				Out.kr(pBus, A2K.kr(head));
				Out.ar(out, sig);
			}).add;
		};
	}

	/**** Sample Play Controls ****/
	prepPlayback {|server|
		server = server ? Server.default;
		this.pr_loadSynthDefs;
		if(positionBus.isNil) {
			positionBus = Bus.new(\control, numChannels: 1, server: server);
		};
	}

  isPlaying { ^playNode.notNil }

	play {|server, out=0, startframe=0, endframe=(-1), amp=1.0|
		var sdef;
		server = server ? Server.default;
		this.pr_checkServerAlive(server, { ^nil });
    this.pr_checkBufferLoaded({^nil});
		this.stop;
		if(endframe == -1 || (endframe > buffer.numFrames)) {
			endframe = buffer.numFrames;
		};
		"playing... % %".format(startframe, endframe).postln;
		if(buffer.numChannels == 2) { sdef = def2ch } { sdef = def1ch };
		playNode = Synth.new(sdef, [\out, out, \buf, buffer, \pBus, positionBus,
			\amp, amp, \start, startframe, \end, endframe]);
		^playNode;
	}
	stop { if(playNode.notNil) { playNode.free; playNode = nil } }
	/**** End Sample Play Controls ****/

}



