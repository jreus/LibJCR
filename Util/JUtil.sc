

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



//////////////////////////////////////////
// Additional methods to existing core classes.

+ String {
	notecps {
		^this.notemidi.midicps;
	}

	// To frequency
	f {
		^this.notecps;
	}

	// To midi note
	m {
		^this.notemidi;
	}

	// postln with formatting
  //postf {|...args| ^this.format(*args).postln; }
}

+ Symbol {
	notecps {
		^this.asString.notecps;
	}

	// To frequency
	f {
		^this.notecps;
	}

	// To midi
	m {
		^this.asString.m
	}
}

+ Server {

	*quickBoot {
		var tmp = Server.internal;
		tmp.options.memSize = 32768;
		Server.default = tmp.boot;
		^tmp;
	}


}

// Color additions
+ Color {
	*orange {|val=1.0, alpha=1.0|
		^Color.new(val, val, 0, alpha);
	}
}

/*
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