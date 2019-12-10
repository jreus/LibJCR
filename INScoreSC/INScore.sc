/**************************************************************
Helpful functionality for using INScore with SuperCollider

(C) 2019 Jonathan Reus
jonathanreus.com
github.com/jreus

Some useful resources on INScore and GUIDO syntax:


______________
TODO:


______________
*******************************************/





/****** USAGE *******

???r = Reaper.new("localhost", 8000);




********************/

INScoreScore {
  var <>numEvents=0;

}

INScore {
  classvar <gmnStreams, <maxNumScores, <>currScore;
  classvar <defaultClef, <defaultMeter;
  classvar <>maxBeatsPerScore=8, <>numEvents=0;
  classvar <voicesPerScore;
  classvar <>scorePositionRange;
  classvar <>netAddr;

	*initClass {
    // TODO:: Can be removed?
		Platform.case(\osx,
      { "You are using OSX".warn },
      { "You are not using OSX".warn },
		);
    defaultClef = ["g", 2];
    defaultMeter = [4, 4];
    this.maxNumScores_(3, -0.6, 0.6);
    netAddr = NetAddr("localhost", 7000); // inscore listens on port 7000
    this.pr_addINScoreEvents;
	}


  *maxNumScores_ {|newMax, posMin, posMax|
    maxNumScores = newMax;
    scorePositionRange = [posMin, posMax];
    currScore = 1;
    gmnStreams = Array.newClear(newMax);
    newMax.do {|idx|
      gmnStreams[idx] = (open: False, numBeats: 0, maxBeats: maxBeatsPerScore, scoreAddr: "/ITL/scene/score"++(idx+1), clef: defaultClef, meter: defaultMeter);
    };
    // TODO: What are new scores set to?
  }

  // Convenience method for sending messages to INScore
  *send {|obj, method ... args|
    var na = this.netAddr;
    switch(args.size,
      0, { na.sendMsg(obj, method) },
      1, { na.sendMsg(obj, method, args[0]) },
      2, { na.sendMsg(obj, method, args[0], args[1]) },
      3, { na.sendMsg(obj, method, args[0], args[1], args[2]) },
      4, { na.sendMsg(obj, method, args[0], args[1], args[2], args[3]) },
      5, { na.sendMsg(obj, method, args[0], args[1], args[2], args[3], args[4]) },
      6, { na.sendMsg(obj, method, args[0], args[1], args[2], args[3], args[4], args[5]) },
      7, { na.sendMsg(obj, method, args[0], args[1], args[2], args[3], args[4], args[5], args[6]) },
    );
    "SENDING: % args % % %".format(args.size, obj, method, args).warn;
  }


  *init {|numScores=3, beatsPerScore=8, voices=2, clef, meter|
    clef = clef ? ["g", 2];
    meter = meter ? [4, 4];
    this.maxBeatsPerScore = beatsPerScore;
    this.maxNumScores = numScores;
    defaultClef = clef;
    defaultMeter = meter;
    voicesPerScore = voices;
    this.maxNumScores_(numScores, -0.6, 0.6);
    this.netAddr.sendMsg("/ITL/scene/*", "del");
  }

/*---------
   _______ _    _ _______ ______  _______    _
(_______) |  | (_______)  ___ \(_______)  | |
 _____  | |  | |_____  | |   | |_          \ \
|  ___)  \ \/ /|  ___) | |   | | |          \ \
| |_____  \  / | |_____| |   | | |_____ _____) )
|_______)  \/  |_______)_|   |_|\______|______/

-----------*/

	*pr_addINScoreEvents {
    "Adding Events: \\gmnWrite \\gmnWriteNote".warn;

    // TODO: Raw gmn stream (just commands to an address)

    // RAW GMN WRITE TO THE ACTIVE SCORE
    Event.addEventType(\gmnWrite, {
      var write;
      var stream, dur, gmnDur;
      write = ~write ? "_"; // write a rest by default
      stream = INScore.gmnStreams[(INScore.currScore - 1)];
      "write: % '%'".format(stream.scoreAddr, write).warn;
      INScore.netAddr.sendMsg(stream.scoreAddr, "write", write);
      //stream.numBeats = stream.numBeats + 1;
    });

    // MULTI-SCORE GMN NOTE STREAM
    // USING THIS EVENT ASSUMES YOU HAVE CALLED
    // INScore.init(...) with the score parameters you are
    // interested in using...
    Event.addEventType(\gmnWriteNote, {|serv|
			var write="_", cond, pid, thisEvent;
      var stream, dur, amp, rest = false;
      var noteval, numBeatsInEvent;
      var instrument, freq, playNote = false;
      dur = ~dur ? (1/4);
      amp = ~amp ? 0.5;
      if(~freq.notNil) { noteval = ~freq.cpsmidi.midinote };
      if(~note.notNil) { noteval = ~note };
      if(noteval.notNil) {
        rest = [\rr, \rest, \r].includes(noteval);
        if(rest.not) {
          freq = noteval.f;
        };
        write = noteval.gmn(dur);
      };
      if(~write.notNil) { write = ~write };
      if(~synth == \on) {
        instrument = ~instrument; playNote = true;
      };
      // get the current stream
      stream = this.gmnStreams[this.currScore-1];

      if(stream.numBeats >= stream.maxBeats) {
        // close prev score
        this.netAddr.sendMsg(stream.scoreAddr, "write", "]");
        stream.open = False;

        // Make a new score & write the event to it
        this.currScore = this.currScore + 1;
        if(this.currScore > this.maxNumScores) { this.currScore = 1 };
        // prepare next score for writing
        stream = this.gmnStreams[this.currScore-1];
      };

      // check if stream is open
      if(stream.open == False) {
        //this.netAddr.sendMsg(stream.scoreAddr, "set", "gmnstream", "[\\clef<\"f4\">");
        this.netAddr.sendMsg(stream.scoreAddr, "set", "gmnstream", "[");
        this.netAddr.sendMsg(stream.scoreAddr, "y", this.currScore.linlin(1, this.maxNumScores, this.scorePositionRange[0], this.scorePositionRange[1]));
        stream.open = True;
        stream.numBeats = 0;
      };

      // add bar if needed
      if((stream.numBeats != 0).and { (stream.numBeats % stream.meter[0]) == 0 }) {
        write = " \\bar " + write;
      };

      // write whatever there is to write
      "write: % '%'".format(stream.scoreAddr, write).warn;
      this.netAddr.sendMsg(stream.scoreAddr, "write", write);

      // play a note
      if(playNote) {
       (instrument: instrument, freq: freq, amp: amp, dur: dur).play;
      };

      // How many beats are in this write?
      numBeatsInEvent = dur * stream.meter[1]; // one quarter
      stream.numBeats = stream.numBeats + numBeatsInEvent;

		}, (freq: \c5.f, note: \c5, write: nil, instrument: nil, amp: 0.5, dur: 1/4));
	}


  // Generate X random GMN events
  *gmnRandom {|numEvents=4|
    var res, rnd, event;
    res = numEvents.collect {|idx|
      rnd = [\note, \rest].wchoose([0.7, 0.3]);
      switch(rnd,
        \note, {
          event = "abcdefg".choose.asString;
          event = event ++ ["#","&",""].wchoose([0.2,0.2,0.6]);
          event = event ++ rrand(-1, 2);
          event = event ++ "/" ++ [1,2,4,8,16].choose;
        },
        \rest, {
          event = "_";
          event = event ++ "/" ++ [4,8,16].choose;
        }
      );
    };
    res = res.join(" ");
    ^res;
  }

  *guidofy {|notes|
    var res = "";
    if(notes.class == Symbol) { notes = [notes] };
    notes.do {|note, idx|
      var str = note.asString, sz = str.size;
      var mod = str[1], num = str.wrapAt(-1);
      if(mod == num) { mod = "" } {
        if(mod == $s) { mod = "#" };
        if(mod == $b) { mod = "&" };
      };
      num = num.asString.asInt - 5;
      str = str[0..0] ++ mod ++ num;
      if(idx != 0) {
        res = res + str;
      } {
        res = res ++ str;
      };
    };
    ^res;
  }

}

//\abs + 1;

/***** Guido Music Notation Utilities ******/
+ Symbol {
  // convert note symbol to gmn string
  gmn {|dur|
    var str, mod, num, sz;
    if([\r, \rr, \rest].includes(this)) {
      // Process rests differently
      str = "_";
    } {
      str = this.asString; sz = str.size;
      mod = str[1]; num = str.wrapAt(-1);
      if(mod == num) { mod = "" } {
        if(mod == $s) { mod = "#" } { mod = "&" };
      };
      num = num.asString.asInt - 4;
      str = str[0..0] ++ mod ++ num;
    };
    // add duration
    if(dur.notNil) {
      dur = dur.asFraction;
      dur = dur[0].asString ++ "/" ++ dur[1];
      str = str ++ "*" ++ dur;
    };
    ^str;
  }

  // Transpositions
  tp{|semitones|
    ^(this.m + semitones).midinote;
  }
  transpose{|semitones|
    ^this.tp
  }
  ++{|semitones|
    var res = this;
    if(semitones.isInteger) {
      res = this.tp(semitones);
    }
    ^res;
  }
  --{|semitones|
    var res = this;
    if(semitones.isInteger) {
      res = this.tp(semitones * -1);
    }
    ^res;
  }

  notemidi{
    ^this.asString.notemidi;
  }
}


+ SequenceableCollection {
  // convert array of notes to a gmn chord string
  gmn {|dur|
    ^("{" + this.collect(_.gmn(dur)).join(", ") + "}");
  }

  // convert array of notes to a gmn string sequence
  gmnString {
    ^this.collect(_.gmn).join(" ");
  }

  // TRANSPOSIIONS
  tp{|semitones|
    ^this.collect(_.tp(semitones));
  }
  transpose{|semitones|
    ^this.tp(semitones);
  }
}

+ Rest {
  gmn {|dur|
    if(dur.notNil) { dur = dur * this.value };
    ^(\rr.gmn(dur));
  }
}

// Overwrites from midinote.sc in SC3plugins
+SimpleNumber {
	midinote {
		var midi, notes;
		midi = (this + 0.5).asInteger;
		notes = ["c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b"];
		^(notes[midi%12] ++ midi.div(12)).asSymbol
	}
}

+String {
	notemidi {
		var twelves, ones, octaveIndex, midis;
		midis = Dictionary[($c->0),($d->2),($e->4),($f->5),($g->7),($a->9),($b->11)];
		ones = midis.at(this[0].toLower);
		if( (this[1].isDecDigit), {
			octaveIndex = 1;
		},{
			octaveIndex = 2;
			if( (this[1] == $#) || (this[1].toLower == $s) || (this[1] == $+), {
				ones = ones + 1;
			},{
				if( (this[1] == $b) || (this[1].toLower == $f) || (this[1] == $-), {
					ones = ones - 1;
				});
			});
		});
		twelves = (this.copyRange(octaveIndex, this.size).asInteger) * 12;
		^(twelves + ones)
	}
}

+SequenceableCollection {
	midinote { ^this.performUnaryOp('midinote') }
	notemidi { ^this.performUnaryOp('notemidi') }
}

