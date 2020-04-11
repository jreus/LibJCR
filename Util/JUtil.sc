

JUtil { // Handy utility methods
	classvar catchKeyWin;
	classvar jfps;
	classvar <errorChecking = 3;
	classvar meter, scope, freqScope, treePlot;

	*showAll {
		Window.allWindows.do {|win| win.front };
	}

	*pwd {
		^thisProcess.nowExecutingPath.dirname;
	}

	*audioSetup {
		"open -a 'Audio MIDI Setup'".unixCmd;
	}

	*catchKeys {|callback|
		callback.isNil && { callback = {|view,char,mods,unicode,keycode| [char,keycode].postln}};

		catchKeyWin.isNil && { catchKeyWin = Window.new("I catch keystrokes", Rect(128,64,400,50)); };

		catchKeyWin.view.keyDownAction = callback;
		catchKeyWin.front;
	}

	*fpsbang {
		if(jfps.isNil) {
			jfps = JFPS.new();
		};
		^jfps.bang();
	}

	*fps {
		if(jfps.isNil) {
			jfps = JFPS.new();
		};
		^jfps.fps();
	}

	*debug {|printstring, errorlevel=3|
		if(errorlevel <= errorChecking) {
			var result = case
			{errorlevel == 1} { "!!!! ERROR: "; }
			{errorlevel == 2} { "!! WARNING: "; }
			{errorlevel == 3} { "! DEBUG: "; };

			result = result + printstring;
			Post << result << $\n;
		};
	}

	*monitor {arg serv, keepontop=false, plottree=false;
		meter = serv.meter;
		meter.window.alwaysOnTop_(keepontop).setTopLeftBounds(Rect(1320, 0, 100, 200));
		freqScope = serv.freqscope;
		freqScope.window.alwaysOnTop_(keepontop).setTopLeftBounds(Rect(900, 520, 100, 200));
		scope = serv.scope(2, 0);
		scope.window.alwaysOnTop_(keepontop).setTopLeftBounds(Rect(900, 0, 400, 500));
		if(plottree) {
		treePlot = Window.new("Node Tree", scroll: true).front.alwaysOnTop_(false);
		serv.plotTreeView(0.5, treePlot);
		treePlot.setTopLeftBounds(Rect(600, 0, 300, 850));
		};
	}
}

// A frames per second counter
JFPS {
	var avgperiod, lasthit;

	*new {
		^super.new.init;
	}

	init {
		avgperiod = 1.0;
		lasthit = 0.0;
	}

	// Call this each time a frame happens.
	// Returns the current FPS value
	bang {
			var thishit, period;
			thishit = Process.elapsedTime();
			period = thishit - lasthit;
			lasthit = thishit;
			avgperiod = (avgperiod + period) / 2.0;
			^avgperiod;
	}

	fps {
		^avgperiod;
	}
}


// Add timestamped entries to a log file.
JLog {
	var <>fpath;
	classvar logger;

	*new {|filepath, filename|
		^super.new.init(filepath, filename);
	}

	*getLogger {|filepath, filename|
		if(this.logger.isNil) {
			this.logger = JLog.new(filepath, filename);
		};
		^this.logger;
	}

	*log {|str=""|
		this.getLogger.log(str);
	}

	init {|filepath=nil, filename=nil|
		if(filename.isNil) {
			filename = "log_sc_program" ++ ".txt";
		} {
			filename = filename ++ ".txt";
		};

		if(filepath.isNil) {
			// Log to same directory as running process
			fpath = JUtil.pwd +/+ filename;
		} {
			fpath = filepath +/+ filename;
		};
	}

	log {|str|
		var timestamp, result, fh;
		timestamp = Date.getDate.asString;
		result = timestamp + " ### " + str + "\n";
		fh = File.open(fpath, "a");
		fh.write(result);
		fh.close();
		result.postln;
		// ALT: similar to python's with X open Y
		//f = File.use(~d_path, "a", {|thefile| thefile.write("Write somethin else "+rrand(200,300)+"\n");});

	}
}



/* ------------------------------------------------
//////////////////////////////////////////
// ADDITIONS TO CORE CLASSES

------------------------------------------------ */


/* -------------------------------
GENERAL
Server.
Color additions.
Etc.
----------------------- */
+ Server {
	*quickBoot {
		var tmp = Server.internal;
		tmp.options.memSize = 32768;
		Server.default = tmp.boot;
		^tmp;
	}
}

+ Color {
	*orange {|val=1.0, alpha=1.0|
		^Color.new(val, val, 0, alpha);
	}
}

+ Char {
  isInteger {
    ^this.ascii.inclusivelyBetween(48, 57);
  }
}




/*------------------------------------
DATE TIME

Date.getDate.rawSeconds / 86400; // days
Date.getDate.rawSeconds
Date.getDate.daysSinceEpoch
Date.getDate.calcFirstDayOfYear
d = "181230_150017";
e = "190105_000000";
Date.getDate.daysDiff(Date.fromStamp(e))
*/
+ Date {

  /*
  Creates a date object from a datetime stamp of the format YYMMDD_HHMMSS
  */
  *fromStamp {|datetimestamp|
    ^super.new.initFromStamp(datetimestamp);
  }

  initFromStamp {|dts|
    if(dts.isNil.or { dts.isKindOf(String).not.or { dts.size != 13 } }) {
      throw("Bad Time Stamp Format %".format(dts));
    };
    year = dts[..1].asInt;
    year = if(year<70) { year+2000 } { year+1900 };
    month = dts[2..3].asInt;
    day = dts[4..5].asInt;
    hour = dts[7..8].asInt;
    minute = dts[9..10].asInt;
    second = dts[11..12].asInt;
   // dayOfWeek = ; // TODO
    this.calcSecondsSinceEpoch;
  }

  /*
    Returns number of days since civil 1970-01-01.  Negative values indicate
    days prior to 1970-01-01.
  Preconditions:  y-m-d represents a date in the civil (Gregorian) calendar
    m is in [1, 12]
    d is in [1, last_day_of_month(y, m)]
    y is "approximately" in [numeric_limits<Int>::min()/366, numeric_limits<Int>::max()/366]
    Exact range of validity is:
    [civil_from_days(numeric_limits<Int>::min()),
    civil_from_days(numeric_limits<Int>::max()-719468)]
*/
  daysSinceEpoch {
    var era, yoe, doy, doe, res;
    var y=year,m=month,d=day;
    y = y - (m <= 2).asInt;
    era = if(y>=0) {y} {y-399}; era = era / 400;
    yoe = y - (era * 400);      // [0, 399]
    doy = (153*(m + (m > 2).if({-3},{9})) + 2)/5 + d-1;  // [0, 365]
    doe = yoe * 365 + yoe/4 - yoe/100 + doy;         // [0, 146096]
    res = era * 146097 + doe.asInt - 719468;
    ^res;
  }

  calcSecondsSinceEpoch {
    var res;
    res = this.daysSinceEpoch + second + minute*60 + hour*3600;
    this.rawSeconds_(res);
    ^res;
  }

  /*
  This will give the day of the week as 0 = Sunday, 6 = Saturday. This result can easily be adjusted by adding a constant before or after the modulo 7
  */
  calcFirstDayOfYear {
    var res;
    res = (year*365 + trunc((year-1) / 4) - trunc((year-1) / 100) + trunc((year-1) / 400)) % 7;
    ^res;
  }

  /*
  this-that in days
  */
  daysDiff {|that|
    ^(this.daysSinceEpoch - that.daysSinceEpoch)
  }
}

/* -------------------------------------
MUSIC NOTATION
and RAPID PLAYBACK
-------------------------------- */

+ Synth {
  gate {|val=0|
    ^this.set(\gate, val);
  }
}

+ String {
  notecps { ^this.tomidi.midicps; }

  f { ^this.notecps; } // to frequency

  m { ^this.tomidi; } // to midi

  // convert note symbol as string into midi note
  tomidi {
	var twelves, ones, octaveIndex, midis, octave=false;
	midis = Dictionary[($c->0),($d->2),($e->4),($f->5),($g->7),($a->9),($b->11)];
	ones = midis.at(this[0].toLower);
	if( this.size > 1 ) {
		if(this[1].isDecDigit) {
			octave=true;
			octaveIndex = 1;
		} {
			if( (this[1] == $#) || (this[1].toLower == $s) || (this[1] == $+) ) {
				ones = ones + 1;
			} {
				if( (this[1] == $b) || (this[1].toLower == $f) || (this[1] == $-) ) {
					ones = ones - 1;
				};
			};
			if( this.size > 2 ) {
				octaveIndex = 2;
				octave=true;
			};
		};
	};
	if(octave) {
		twelves = (this.copyRange(octaveIndex, this.size).asInteger) * 12;
	} {
		twelves = 5*12; // default without octave indicator
	};
	^(twelves + ones);
  }


  // Convert a string of note values
  // to an array of note symbols
  // "ab2 bb2 c3" becomes [\ab2, \bb2, \c3]
  notes {
    var res;
    var register = 5;
    // remove any newlines or excess whitespace
    res = this.stripWhiteSpace.replace($\n, " ").replace("   ", " ").replace("  ", " ");
    res = res.split($ );
    res = res.collect {|notestr|
      var notesymbol, notereg;
      if(["r", "rest", "rr"].includesEqual(notestr)) {
        notesymbol = \rr
      } {
        if([$&, $b, $f].includes(notestr[1])) {
          notestr[1] = $b;
        };
        if([$#, $s].includes(notestr[1])) {
          notestr[1] = $s;
        };
        if(notestr.wrapAt(-1).isInteger) {
          register = notestr.wrapAt(-1).asString.asInt;
        } {
          notestr = notestr ++ register;
        };
        notesymbol = notestr.asSymbol;
      };
      notesymbol;
    };
    ^res;
  }


  // Convert a string of numbers - fractional or decimal - into an array of durations
  // "1/2 0.23 1" becomes [0.5, 0.23, 1]
  durs {
    var res;
    // remove any newlines or excess whitespace
    res = this.stripWhiteSpace.replace($\n, " ").replace("   ", " ").replace("  ", " ");
    res = res.split($ );
    res = res.collect {|durstr|
		var val;
		if(durstr.includes($/)) {
			val = durstr.interpret;
		} {
			val = durstr.asFloat;
		};
		val;
    };
    ^res;
  }
  // convert a string of notes into frequencies
  freqs { ^this.notes.f }

  // play a scale of intervals (half step/whole step)
  // "--.---.".play; // major diatonic scale
  playScale {|root=\c5, tuning=\et12, amp=0.2, dur=0.5, delta=0.6, pan=0.0|
    var etsemitone = 2**(1/12), etwhole = 2**(1/6);
    if(root.isNumber.not) { root = root.f };
    {
      this.do {|char|
        root.play(amp, pan, dur);
        root.postln;
        if(char == $-) { // whole step
          root = root * etwhole;
        };
        if(char == $.) { // half step
          root = root * etsemitone;
        };
        delta.wait;
      };
      root.play(amp, pan, dur);
    }.fork;
    ^this;
  }

	// postln with formatting
  //postf {|...args| ^this.format(*args).postln; }
}


+ Symbol {
  notecps { ^this.asString.notecps; }

  // To frequency
  f { ^this.notecps; }

  // To midi
  m { ^this.asString.m; }

  // Plays note symbols as notes
  play {|amp=0.2, pan=0.0, dur=0.5|
    ^this.notecps.play(amp, pan,dur);
  }

  // Transpositions & Smart Manipulations
  tp{|semitones|
    var res = this;
    if([\r, \rr, \rest].includes(this).not) {
      res = (this.m + semitones).midinote;
    };
    ^res;
  }
  transpose{|semitones|
    ^this.tp(semitones)
  }

  /*
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
  */


  // diverge/converge away/towards a specific key
  // with a "strength" factor
  diverge {|key, scale, strength=0.5|
    var res = this;
    key = key ? \g;
    scale = scale ? Scale.major;
    ^res;
  }
  converge {|key, scale, strength=0.5|
    var res = this;
    key = key ? \g;
    scale = scale ? Scale.major;
    ^res;
  }

  // convert note symbol to midi note
  notemidi {
    ^this.asString.notemidi;
  }
}

+ SimpleNumber {
  play {|amp=0.2, pan=0.0, dur=0.5| // plays number as frequency
    var syn = {
      var numharms=10, falloff=0.6, sig;
      var freqs = Array.newClear(numharms);
      var amps = Array.newClear(numharms);
      numharms.do {|idx|
        freqs[idx] = this * (idx+1);
        amps[idx] = falloff**idx;
      };
      sig = SinOsc.ar(freqs, mul: amps).sum;
      Pan2.ar(sig, pan, amp) * EnvGen.ar(Env.perc, timeScale: dur, doneAction: 2)
    }.play;
    ^syn;
  }

  // Overwrites from midinote.sc in SC3plugins
  midinote {
	var midi, notes;
	midi = (this + 0.5).asInteger;
	notes = ["c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b"];
	^(notes[midi%12] ++ midi.div(12)).asSymbol
  }

}

/* ------------
Collections
-----------*/

+ SequenceableCollection {
  // to frequency & midi note
  f { ^this.collect(_.f); }

  m { ^this.collect(_.m); }

  play {|amp, pan, dur=0.5, delta=0.6|
    {
      this.do {|note|
        note.play(amp, pan, dur);
        delta.wait;
      }
    }.fork;
    ^this;
  }

  midinote { ^this.performUnaryOp('midinote') }
  notemidi { ^this.performUnaryOp('notemidi') }

  // TRANSPOSIIONS
  tp {|semitones| ^this.collect(_.tp(semitones)); }

  transpose {|semitones| ^this.tp(semitones); }
}



