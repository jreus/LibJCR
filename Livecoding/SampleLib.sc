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




/******************************************************************

@usage
SampleLib.load(); // loads global & local sample libraries

SampleLib.at("drum001"); // get sample by id, or search library by various functions
// e.g. by type, by directory, etc..

SampleLib.gui; // open gui editor

*******************************************************************/

SampleLib {
	classvar <samples; // all available sample files by id
	classvar <samplesByGroup; // available sample files, by group name
	classvar <globalSamplesPath; // global samples directory
	classvar <localSamplesPath; // local samples directory

	classvar <activeServer; // server where buffers are allocated and loaded, should be set on load

	classvar <win; // gui for navigating sample library

	// boolean toggles lazyloading of samples, false by default. If true will save some ram by only loading samples when necessary
	// However, keep in mind that samples are loaded into buffers on the server asynchronously from other language instructions,
	// and thus you must be wary of performing operations on buffer variables still holding nil.
	classvar <lazyLoadGlobal=true, <lazyLoadLocal=false;

	*initClass {
		samples = Dictionary.new;
		samplesByGroup = Dictionary.new;
		globalSamplesPath = "~/_samples".absolutePath;
	}


	// private method
	*pr_checkServerAlive {|inactiveFunc|
		if(this.activeServer.serverRunning.not) {
			error("Cannot Allocate Buffers: Boot the Server First!");
			inactiveFunc.value;
		};
	}

	// By default will look for a local 'samples' directory in the same directory as the current document.
	// A global samples directory will be searched for at globalSamplesPath
	// If a server is not provided, server will be the default server.
	*load {|server=nil,localsampledir=nil,lazyload=false|
		var samplePath;

		if(server.isNil) { server = Server.default };
		activeServer = server;
		this.pr_checkServerAlive({^nil});

		if(localsampledir.isNil) { localsampledir = Document.current.dir +/+ "_samples" };
		localSamplesPath = localsampledir;
		if(File.exists(localSamplesPath).not) { File.mkdir(localSamplesPath) };
		if(File.exists(globalSamplesPath).not) { File.mkdir(globalSamplesPath) };

		activeServer = server;

		// Load samples from global & local paths
		"Loading Global Samples ... %".format(globalSamplesPath).postln;
		samplePath = PathName.new(globalSamplesPath);
		this.pr_loadSamples(samplePath, lazyLoadGlobal);
		"Loading Local Samples ... %".format(localSamplesPath).postln;
		samplePath = PathName.new(localSamplesPath);
		this.pr_loadSamples(samplePath, lazyLoadLocal);
	}

	// private method
	*pr_loadSamples {|samplePath, lazyLoad|
		samplePath.filesDo {arg path;
			var md; // metamd
			var id, group, groupId;
			md = SampleFile.openRead(path);
			if(md.notNil && md.isSoundFile) {
				"... %".format(md.path).postln;
				//[md.duration, md.headerFormat, md.sampleFormat, md.numFrames, md.numChannels, md.sampleRate, path.folderName, path.extension, path.fileName, md.path].postln;

				id = path.fileNameWithoutExtension.replace(" ","");
				samples.put(id, md);
				groupId = path.folderName.asSymbol;
				group = samplesByGroup.at(groupId);
				if(group.isNil) { // new group
					group = Dictionary.new;
					samplesByGroup.put(groupId, group);
				};
				group.put(id, md);

				if(lazyLoad == false) { // if lazyload not active, go ahead and load everything
					md.loadFileIntoBuffer(activeServer);
				};

			};
		};
	}

	// When lazyloading is active, preload lets you preload groups of samples
	*preload {
		/*** STUB ***/
	}

	// Returns the SampleFile for a given id
	// If the file is not loaded into a buffer, this method will cause it to be so
	*at {|id|
		var sample, result = nil;
		pr_checkServerAlive({^nil});

		sample = this.samples[id];
		if(sample.notNil) {
			var tmp;
			tmp = sample.buffer;
			if(tmp.isNil) {
				"Load file into buffer".postln;
				tmp = sample.loadFileIntoBuffer(this.activeServer);
			};
			result = tmp;
		};
		^result;
	}


	*gui {
		var styler, subStyler, decorator, childView, childDecorator;
		var searchText, searchList, findFunc;
		var gui_width=250;

		if(win.notNil) { win.close };
		win = Window("Sample Lib", gui_width@800);
		styler = GUIStyler(win); // initialize with master win
		win.view.decorator = FlowLayout(win.view.bounds);
		decorator = win.view.decorator;
		decorator.margin = Point(0,0);

		// Child win
		win.view.bounds.postln;
		childView = styler.getWindow("SubView", win.view.bounds, scroll: true);
		childView.decorator = FlowLayout(childView.bounds);
		childDecorator = decorator;

		styler.getSizableText(childView, "SAMPLES3", gui_width, \center, 12);

		// Search
		styler.getSizableText(childView, "search", 90, \left, 10);

		searchText = TextField(childView, gui_width@20);

		searchList = ListView(childView, gui_width@100)
		.items_(samples.keys.asArray)
		.stringColor_(Color.white)
		.background_(Color.clear)
		.hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5));

		samples.values.sort({|a,b| a.name < b.name}).do{|samp|
			var subView;
			styler.getSizableText(childView, samp.asString.toUpper, gui_width - 100, \left, 10);
			styler.getSizableButton(childView, "prev", size: 50@20)
			.action = {|btn|
				"Playing %: % % %".format(samp, samp.numChannels, samp.sampleRate, samp.duration).postln;
				samp.play();
			};
		};
		win.front;
	}
}


SampleFile : SoundFile {
	var <buffer;    // buffer, if loaded, on the server where this soundfile exists
	var <>name;

	var <usage;     // rhythmic, one-shot, field recording, etc...
	var <genre;     // european, house, techno, folk, etc...
	var <source;    // voice, strings, pad, mono-synth, etc...

	// maybe fancier analysis-related things? Could also be generated and stored in meta-data files.
	var frequency_profile;
	var average_tempo;
	var average_pitch;
	var markers; // time marker points

	/*
	@Override
	NOTE: This needs to be redone to return an instance of SampleFile, I might actually need to rewrite SoundFile
	*/
	*openRead {arg path;
		^super.new.init(path);
	}

	// MAYBE, need to overwrite openRead to add loading of metamd or analysis
	/*
	*openRead { arg pathName;
	var file;
	file = SoundFile(pathName);
	if(file.openRead(pathName)) { ^file } { ^nil }
	}
	*/


	init {arg path;
		this.openRead(path.asAbsolutePath);
		name = path.fileNameWithoutExtension.replace(" ","");

	}

	isSoundFile {
		^this.headerFormat.matchRegexp("[WAV|WAVEAIFF]");
	}

	/*
	@param server The server to load this buffer into
	@param action Function to be evaluated once the file has been loaded into the server. Function is passed the buffer as an argument.
	*/
	loadFileIntoBuffer {arg server, action=nil;
		var newbuf;
		if(server.isNil) {
			server = Server.default;
		};
		if(server.serverRunning.not) {
			error("Server is not running, cannot load file into memory: Boot the Server First!");
			^nil;
		};
		"Loading into memory %".format(this.path).postln;
		newbuf = Buffer.read(server, this.path, action: action);
		buffer = newbuf;
		^buffer;
	}

	bufnum {
		^buffer.bufnum;
	}

	asString {
		^("SampleFile" + name);
	}

	play {|serv|
		var responseFunc;
		if(serv.isNil) { serv = Server.default };
		responseFunc = {|buf|
			if(buf.isNil) {
				"Cannot play %".format(name).postln;
				^nil;

			} {
				buf.play;
			};
		};
		if(buffer.isNil) {
			^this.loadFileIntoBuffer(serv, responseFunc);
		} {
			buffer.play;
		};

	}

}



