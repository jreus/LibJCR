/******************************************
General SC Project Class for workflow

(C) 2019 Jonathan Reus

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

https://www.gnu.org/licenses/

_____________________________________________________________
Project
A project system for livecoding and general SuperCollider projects
that wraps Scenes, Macros, Smpl, Syn and other major workflow modules.

@usage
Create a file called _main.scd inside your project root folder.
Subdirectories for things like macros, scenes, and samples will be
greated within when you initialize the project for the first time.

Inside _main.scd call

~sc = Scenes.new;

f = "".resolveRelative +/+ "scenes";
z = Scenes(f).makeGui;
________________________________________________________________*/

Project {
  classvar <>samplerates;
  classvar <>blocksizes;
  classvar <>devicepreference;
  classvar <meters;
  classvar <reaperSim, <>isReaperSim = false, <>scOnly = true;
  classvar <>memSize=1048576, <>numBuffers=4096;
  classvar <>limitSamplesLocal=1000, <>limitSamplesGlobal=1000;

  classvar <garbage;

  // main outs, bypassing Reaper
  classvar <>outmain = 0;

  // internal stereo audio busses to reaper
  classvar <>outclean=10, <>outverb=12, <>outbass=14;


  *initClass {
    samplerates = [22050, 44100, 48000, 88200, 96000];
    blocksizes = [16,32,64,128,256,512,1024];
    devicepreference = [
      "UltraLite AVB Jon",
      "AudioHub BuiltIn",
      "AudioHub SBP",
      "AudioHub FA101",
    ];
    garbage = List.new;
  }

  *meter {|server|
    var bnd, len;
    server = server ? Server.default;
    if(meters.isNil.or { meters.window.isClosed }) {
      meters = server.meter;
      meters.window.alwaysOnTop_(true).front;
      bnd = meters.window.bounds;
      len = Window.screenBounds.width - bnd.width;
      meters.window.bounds = Rect(len, 0, bnd.width, bnd.height);
    } {
      meters.window.front;
    };
    ^meters;
  }

  // Use this every time a temporary process such as a synth, ndef, or pdef is created
  *collect {|thesynth|
    garbage.add(thesynth);
  }

  // clean up all synths & other extraneous processes
  *cleanup {
    garbage.do {|proc|
      if(proc.notNil) {
        if(proc.isKindOf(Synth)) {
          proc.free;
        };
        if(proc.isKindOf(Ndef)) {
          proc.clear;
        };
        if(proc.isKindOf(Pdef)) {
          proc.clear;
        };
        if(proc.isKindOf(Tdef)) {
          proc.stop;
        };
      };
    };
    garbage = List.new;
  }

  // hard reset, when in need of a hard reboot
  *reset {
    MPK.initMidiFuncs;

  }


  *startup {|server=nil, verbose=false, showScenes=true, showMeters=true, limitSamplesLocal, limitSamplesGlobal, scOnly, rootpath=nil, scenedir=nil, localSamplePaths=nil, onBoot=nil|

    if(limitSamplesLocal.notNil) {
      this.limitSamplesLocal = limitSamplesLocal;
    };
    if(limitSamplesGlobal.notNil) {
      this.limitSamplesGlobal = limitSamplesGlobal;
    };

    if(scOnly.notNil) { Project.scOnly = scOnly };

    // Init non-server dependent modules
    Scenes.init(rootpath, scenedir);
    Macros.load(Scenes.rootPath +/+ "_macros/");
    if(showScenes) { Scenes.gui };

    if(localSamplePaths.isNil) { // use default project structure local samples directory
      localSamplePaths = [Scenes.rootPath +/+ "_samples/"];
      if(File.exists(localSamplePaths[0]).not) { File.mkdir(localSamplePaths[0]) };
    };

    Project.pr_makeStartupWindow(server, localSamplePaths, onBoot, showMeters, verbose);
  }


  *pr_makeStartupWindow {|server, localSamplePaths, onBoot, showMeters, verbose|
var win, styler, container, subview;
    var srdropdown, bsdropdown, devlist, yesbut, nobut, sconly, allsamples;


    if(server.isNil) { server = Server.default };
    win = Window.new("Choose Audio Device", Rect(Window.screenBounds.width / 2.5, Window.screenBounds.height / 2.4,300,200), false);
    styler = GUIStyler(win);
    container = styler.getView("Start", win.view.bounds, margin: 10@10, gap: 10@30);
    styler.getSizableText(container, "samplerate", 55, \right);
    srdropdown = PopUpMenu(container, 70@20).items_(samplerates).value_(1);

    styler.getSizableText(container, "blocksize", 60, \right);
    bsdropdown = PopUpMenu(container, 60@20).items_(blocksizes).value_(2);
    styler.getSizableText(container, "device", 55, \right, 14);
    devlist = PopUpMenu.new(container, Rect(0, 0, 210, 30))
    .font_(Font("Arial", 16))
    .items_(ServerOptions.devices.sort);
    block {|break|
      devicepreference.do {|devname|
        devlist.items.do {|it, idx|
          if(it == devname) {
            devlist.value = idx;
            break.value;
          };
        };
      };
    };

    subview = styler.getView("ScOnly", 50@70, false, 0@0, 0@0, container);
    sconly = RadioButton(subview, 50@50, \check);
    sconly.value = scOnly;
    styler.getSizableText(subview, "SC Only?", 50, \center);

    subview = styler.getView("Load all Samples", 50@70, false, 0@0, 0@0, container);
    allsamples = RadioButton(subview, 50@50, \check);
    styler.getSizableText(subview, "Load All Samples?", 50, \center);

    yesbut = styler.getSizableButton(container, "Start", size: 85@70).font_(Font("Arial", 18));
    nobut = styler.getSizableButton(container, "Cancel", size: 60@70).font_(Font("Arial", 14));

    yesbut.action_({|btn|
      isReaperSim = sconly.value;
      server.options.device = devlist.items[devlist.value];
      server.options.numInputBusChannels = 20;
      server.options.numOutputBusChannels = 20;
      server.options.memSize = memSize;
      server.options.blockSize = bsdropdown.item.asInt;
      server.options.sampleRate = srdropdown.item.asInt;
      server.options.numWireBufs = 512 * 2;
      server.options.numBuffers = numBuffers;
      win.close;
      "BOOT: % %  %".format(
        srdropdown.item.asInt, bsdropdown.item.asInt,
        devlist.items[devlist.value]
      ).warn;
      server.waitForBoot {
        // load server-dependent modules
        Syn.load;
        if(allsamples.value == true) {
          this.limitSamplesGlobal = 50000;
          this.limitSamplesLocal = 50000;
        };
        Smpl.load(server, verbose: verbose, limitLocal: this.limitSamplesLocal, limitGlobal: this.limitSamplesGlobal, localSamplePaths: localSamplePaths, doneFunc: {
          if(showMeters) { this.meter };
          if(isReaperSim) { this.initAudioRouting } { Rea.init };
          if(onBoot.notNil) { onBoot.value };
        });
      };
    });

    nobut.action_({|btn| win.close});

    win.alwaysOnTop_(true).front;
  }

  *initAudioRouting {
    reaperSim = Ndef(\reapersim, {|master=1.0, verbsize=0.7, verbdamp=0.8, verbmix=0.2, bassco=200, bassrq=0.5|
      var sig, inverb, inclean, inbass;
      inverb = InFeedback.ar(outverb, 2);
      inclean = InFeedback.ar(outclean, 2);
      inbass = InFeedback.ar(outbass, 2);
      inverb = FreeVerb.ar(inverb, verbsize, verbdamp, verbmix);
      inbass = BLowPass4.ar(inbass, bassco, bassrq);
      sig = inverb + inclean + inbass;
      Limiter.ar(LeakDC.ar(sig * master), 1.0, 0.001);
    }).play(out: outmain, numChannels: 2);
    ^reaperSim;
  }
}