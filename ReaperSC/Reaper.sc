/******************************************

Control Reaper from SuperCollider

2018 Jonathan Reus
jonathanreus.com
info@jonathanreus.com

MORE INFO ON REAPER OSC CONTROL CAN BE FOUND HERE:





TODO:

Most of the track parameters like number, select, mute, solo, pan,
Global project-wide parameters like time, beat, samples, frames, tempo, play/stop
FX units for each track, FX bypass, parameter names and controls, bypass, preset, openui

A weird condition occurs when a track is deleted, in such cases track numbers may change. There's no real absolute message to tell you a track was deleted. This is because Reaper assumes your control surface will have a fixed number of tracks matched to your Reaper setup.
Probably a script could be written to change this behavior...

Good idea?: write a custom address space to make parsing easier.. all addresses start with /rea/ ?

Feature Requests from Reaper devs:
- OSC notification when a track is deleted or created so that it can be registered on the ctrl surface
- a way for the ctrl surface to ping Reaper for the most updated track info
- OSC method for creating new effects by name on a track
- OSC actions with arguments


NOTE:
Assumes you have two IAC drivers set up
"To Reaper"
and
"To SC"

*******************************************/





/* USAGE: *********************

r = Reaper.new("localhost", 8000);




********************************/


Rea {
  classvar <>tracksById, <>tracksByName, <>tr;
  classvar <oscfunc;
  classvar <reaperAddr;
  classvar <>verbose;
  classvar <midiToReaper; // MIDI endpoint
  classvar <midiFromReaper;
  classvar <oscToReaper; // OSC address


  // Set up MIDI connections & OSC responders
  *init {
    var midiout=nil;
    MIDIClient.init;
    // TODO: Make a source select popup window
    MIDIClient.sources.do {|endpoint|
      if((endpoint.device == "IAC Driver").and { endpoint.name == "To Reaper" }) {
        midiout = endpoint;
      };
    };
    if(midiout.isNil) {
      "MIDI DEVICE NOT FOUND: IAC Driver - To Reaper".throw;
    };
    // connect midi input from reaper
    //MIDIIn.connect(0, midiin);
    midiToReaper = MIDIOut.newByName(midiout.device, midiout.name).latency_(Server.default.latency);
    oscToReaper = NetAddr("localhost", 8000);

    tracksByName = Dictionary.new;
    tr = ();
    tracksById = Array.newClear(30);
  }

  //****** MIDI ******//





  //***** Reaper/SC Control Commands *****//

  // linear fade of track volume
  // track 0 is the master track
  *fadeTrack {|tracknum=0, from=0, to=1.0, dur=1|
    ^{
      // convert from linear float amplitude to Reaper DB
      // The 0dB point in reaper is 0.716
      from = from.ampdb.curvelin(-180, 0, 0.0, 0.716, -3.09);
      to = to.ampdb.curvelin(-180, 0, 0.0, 0.716, -3.09);
      Array.interpolation(100, from, to).do {|i|
        oscToReaper.sendMsg("/track/%/volume".format(tracknum), i);
        (dur/100).wait;
      };
    }.fork(AppClock);
  }

  *pan {|tracknum=0, from=0.0, to=0.0, dur=1|
    ^{
    // scale from SC panning to reaper panning (centered at 0.5)
    from = from.linlin(-1.0, 1.0, 0.0, 1.0);
    to = to.linlin(-1.0, 1.0, 0.0, 1.0);
      Array.interpolation(100, from, to).do {|i|
        oscToReaper.sendMsg("/track/%/pan".format(tracknum), i);
        (dur/100).wait;
      };
    }.fork(AppClock);
  }

  *bypassFx {|tracknum=0, fxnum=0, bypassval=1.0|
    oscToReaper.sendMsg("/track/%/fx/%/bypass".format(tracknum, fxnum), bypassval);
  }

  *setFx {|tracknum=0, fxnum=0, fxparam=0, value=0.0|
    oscToReaper.sendMsg("/track/%/fx/%/fxparam/%/value".format(tracknum, fxnum, fxparam), value);
  }

  *mute {|tracknum=0, muteval=1|
    oscToReaper.sendMsg("/track/%/mute".format(tracknum), muteval);
  }

  *tempo {|bpm=120|
    oscToReaper.sendMsg("/tempo/raw", bpm);
  }

  // input is in bpm
  *rampTempo {|clock, from=60, to=120, dur=1|
    ^{
      from = from / 60;
      to = to / 60;
      Array.interpolation(100, from, to).do {|i|
        clock.tempo_(i);
        oscToReaper.sendMsg("/tempo/raw", i * 60);
        (dur/100).wait;
      };
    }.fork(AppClock);
  }
  //*****END Reaper Control Commands *****//






  /*
  Open Reaper and create a SC instance of Reaper. This method assumes that you have a Reaper project file named SC.RPP
  in the same directory as the active document.
  Only works on OSX.

  @param filepath  An optional filepath to the Reaper project file to open.

  @return  A Reaper instance

  @example
  f = "".resolveRelative +/+ "MyReaper.RPP";
  Reaper.open(f);

  @todo
  */
  *open {arg filepath=nil;
    if(filepath.isNil) {
      filepath = Document.current.dir +/+ "SC.RPP";
    };

    // Open Reaper..
    ("open -a Reaper64"+filepath).runInTerminal;

    ^this.new;
  }

  send {arg addr, val=nil;
    "Sending Msg: % %".postf(addr, val);
    reaperAddr.sendMsg(addr,val);
  }

  // Get track by id or name
  track {arg id;
    if(id.isInteger) {
      ^tracksById[id];
    } {
      ^tracksByName[id];
    };
  }


  init {arg host, port, verbose_osc;
    //tracksById = Array.newClear(64);
    tracksById = Dictionary.new;
    tracksByName = Dictionary.new;
    reaperAddr = NetAddr(host, port);

    verbose = verbose_osc;

    // Set up the OSC listener
    oscfunc = {arg msg, time, addr;
      var matches, tnum, track, cmd, val;

      //msg.postln;
      matches = msg[0].asString.findRegexp("(/track/([0-9][0-9]?))[/]?([/A-Za-z0-9]*)");

      if(matches.size > 2) {
        tnum = matches[2][1].asInt;
        cmd = matches[3][1];
        val = msg[1];
        // If a track doesn't exist by name or by number, create it
        // parse the message, but let the track do that..
        track = tracksById[tnum];
        if(track.isNil) {
          track = ReaperTrack.new(tnum, this);
          tracksById[tnum] = track;
        };
        track.parseCmd(cmd, val);

      };

    };

    thisProcess.addOSCRecvFunc(oscfunc);
  }




}

ReaperTrack {
  var <params;
  var fxunits;
  var setup;

  *new {arg number, parentsetup;
    ^super.new.init(number, parentsetup);
  }

  init {arg number, parentsetup;
    setup = parentsetup;
    params = Dictionary.new;
    params["number"] = number;
    fxunits = Dictionary.new;
  }

  parseCmd {arg cmd, val;
    if(cmd == "volume") {
      params[cmd] = val;
    };
    if(cmd == "name") {
      params[cmd] = val;
      setup.tracksByName[val] = this;
    };
  }

  volume_ {arg val;
    var addr;
    val = val.clip(0,1);
    params["volume"] = val;
    addr = "/track/%/volume".format(params["number"]);
    setup.send(addr, val);
  }


}

ReaperFX {
  var params;
  var parentTrack;
}

