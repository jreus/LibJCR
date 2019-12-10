/******************************************
Manager for multiple instances of a Synthdef.
Similar to Ndef but with more compact syntax for multiple
synthesis processes overlaid.

(C) 2019 Jonathan Reus
GPL 3

*******************************************/



/*--------------------------------------------------------------

@usage


________________________________________________________________*/

Synbank[] {
  var <synthname, <>outbus;
  var <synths, <lastargs;

  *new {|size=10, synthdef, out=0|
    ^super.new.init(size, synthdef, out);
  }

  init {|size, synthdef, out|
    synths = Array.newClear(size);
    lastargs = Array.newClear(size);
    synthname = synthdef;
    outbus = out;
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
    synths[slot] = Synth(synthname, args ++ \out ++ outbus);
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

