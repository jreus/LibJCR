/******************************************
Manager for multiple instances of a Synthdef.
Similar to Ndef but with more compact syntax for multiple
synthesis processes overlaid. Good for oscillator banks and similar.

Creates a group where all synths exist in.

(C) 2019 Jonathan Reus
GPL 3

*******************************************/



/*--------------------------------------------------------------

@usage

Syn.gui;
n = Synbank(10, \sin2, 0);
n.play(1, [\freq, \fs7.f, \fratio, 1.5, \mod1, 0.1, \mod2, 0.11, \amp, 0.04]);
n.play(2, [\freq, \d7.f, \fratio, 1.5, \mod1, 0, \mod2, 1, \amp, 0.02]);
n.play(0, [\freq, \b3.f, \fratio, 1.5, \mod1, 0, \mod2, 0.2, \amp, 0.4]);
n.play(4, [\freq, \c3.f, \fratio, 1.5, \mod1, 0, \mod2, 0.2, \amp, 0.4]);
n.play(3, [\freq, \f8.f, \fratio, 1.5, \mod1, 0.02, \mod2, 10.1, \amp, 0.01]);
n.gate(0);
n.gate(1);
n.outbus = 10;
n.gateAll;

________________________________________________________________*/

Synbank[] {
  var <synthname, <>outbus, <group;
  var <synths, <lastargs;

  *new {|size=10, synthdef, out=0, addAction=\addToHead, target|
    ^super.new.init(size, synthdef, out, addAction, target);
  }

  init {|size, synthdef, out, addAction, target|
	synths = Array.newClear(size);
    lastargs = Array.newClear(size);
    synthname = synthdef;
    outbus = out;
	group = Group.new(target, addAction);
  }

  at {|idx| ^synths[idx]}

  gate {|idx, val=0| synths[idx].set(\gate, val) }

  play {|slot=0, args, crossfade=false|
    if(synths[slot].notNil) {
      if(crossfade) {
        synths[slot].set(\gate, 0);
      } {
        synths[slot].free;
      };
    };
    if(args.isNil) {
      if(lastargs[slot].isNil) {
        "ERROR: No arguments given to new Synth".error;
        ^nil;
      } {
        args = lastargs[slot];
      };
    };
    synths[slot] = Synth(synthname, args ++ \out ++ outbus, target: group);
    lastargs[slot] = args;
    ^synths[slot];
  }

  freeAll {
    synths = synths.collect {|syn|
      if(syn.notNil) { syn.free };
      nil;
    };
 }

  mute {|state=1|
    synths.do {|syn| if(syn.notNil) { syn.set(\mute, state) } }
  }

  gateAll {
    synths.do {|syn| if(syn.notNil) { syn.set(\gate, 0) } }
  }

}


