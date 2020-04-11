/**************************************************************
Helpful functionality for using INScore with SuperCollider

(C) 2019 Jonathan Reus GPLv3
jonathanreus.com
github.com/jreus

Some useful resources on INScore and GUIDO syntax:


______________
TODO:


______________
*******************************************/





/****** USAGE *******



********************/

INScoreScore {
  var <>numEvents=0;

}

INScore {
  classvar <gmnStreams, <numScores=2, <currScore=1;
  classvar <defaultClefs, <defaultMeters;
  classvar <>maxBeatsPerScore=8, <>numEvents=0;
  classvar <voicesPerScore=2, <linesPerScore=2;
  classvar <>scorePositionRange;
  classvar <>netAddr;
  classvar <>verbose = false;

	*initClass {
    // TODO:: Can be removed?
		Platform.case(\osx,
      { "You are using OSX".warn },
      { "You are not using OSX".warn },
		);
    netAddr = NetAddr("localhost", 7000); // inscore listens on port 7000
    this.pr_addINScoreEvents;
    defaultClefs = ["g", 2].dup(voicesPerScore).dup(numScores);
    defaultMeters = [4, 4].dup(numScores);
    this.numScores_(1, -0.6, 0.6);
	}


  // Convenience method for sending osc messages to INScore
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
    if(verbose == true) {
    "SENDING: % args % % %".format(args.size, obj, method, args).warn;
    };
  }

  // Set the number of scores, in doing so the gmnStream are reset
  // usually this method is called by INScore.init(...)
  *numScores_ {|newMax, posMin, posMax|
    numScores = newMax;
    scorePositionRange = [posMin, posMax];
    // array indexed by score number
    // each score contains a member, lines
    // which is an array of lines, each line
    // contains streams for its own voices...
    gmnStreams = numScores.collect {|scoreidx|
      var score, lines, voices;
      // lines
      lines = linesPerScore.collect {|lineidx|
        var line, voicestreamsForLine;
        // streams by line (mainly for display purposes using a score expression)
        voicestreamsForLine = voicesPerScore.collect {|voiceidx|
          var stream;
          stream = "st"++scoreidx++lineidx++voiceidx; // unique stream id
          stream = (id: stream, open: False, numBeats: 0, maxBeats: maxBeatsPerScore, addr: "/ITL/scene/"++stream, clef: defaultClefs[scoreidx][voiceidx], meter: defaultMeters[scoreidx], score: scoreidx, line: lineidx, voice: voiceidx);
          stream;
        };
        line = "ln"++scoreidx++lineidx; // unique line id
        line = (id: line, addr: "/ITL/scene/"++line, voiceStreams: voicestreamsForLine, numStreams: voicesPerScore, line: lineidx, score: scoreidx);
        line;
      };

      // voices
      voices = voicesPerScore.collect {|voiceidx|
        var voice, voicestreamsForVoice;
        // streams by voice
        voicestreamsForVoice = lines.collect {|line|
          var vs = line.voiceStreams;
          vs[voiceidx];
        };
        voice = (streams: voicestreamsForVoice, currStream: 0, numStreams: linesPerScore, score: scoreidx, modifiers: (gliss: 0, slur: 0, trill: 0, pizz: 0, harm: 0));
      };

      score = (voices: voices, lines: lines, numVoices: voicesPerScore, numLines: linesPerScore);
      score;
    };
  }

  /*
  scores          The number of individual (unlinked) scores.
  lines           The number of "lines" a score wraps over.
  voices          The number of linked staffs per score.
  beatsPerScore   The number of beats before a score wraps to the following line or its beginning (depends on linesPerScore)
  clefs           Default clefs, per score, per voice
  meter           Default meters, per score
  USAGE:
  c = [[["g",4],["g",4]],[["f",2],["f",4]]]; // clefs for 2 scores, 2 voices each
  m = [[4,4],[3,2]]; // meters for 2 scores
  INScore.init(2, 2, 2, 8, c, m);
  */
  *init {|scores=1, lines=1, voices=2, beatsPerScore=8, clefs, meters, posRange|
    if(clefs == \bass) {
      clefs = ["f", 4].dup(voices).dup(scores);
    };
    if(clefs == \treble) {
      clefs = ["g", 2].dup(voices).dup(scores);
    };
    clefs = clefs ? ["g", 2].dup(voices).dup(scores);
    meters = meters ? [4, 4].dup(scores);
    posRange = posRange ? [-0.6, 0.6];

    if(clefs.size != scores) {
      Exception("Bad number of clef specifications %".format(clefs)).throw;
    } {
      clefs.do {|clefsForScore, idx|
        if(clefsForScore.size != voices) {
          Exception("Bad number of clef specifications for score %: %".format(idx, clefs)).throw;
        }
      }
    };
    if(meters.size != scores) {
      Exception("Bad number of meter specifications %").format(meters).throw;
    };

    linesPerScore = lines;
    voicesPerScore = voices;
    maxBeatsPerScore = beatsPerScore;
    defaultClefs = clefs;
    defaultMeters = meters;
    // initialize gmnstreams with a positional range
    this.numScores_(scores, posRange[0], posRange[1]);
    this.netAddr.sendMsg("/ITL/scene/*", "del");

    // create all the INScore objects
    gmnStreams.do {|score, scoreidx|
      var voices, lines, expr;
      voices = score.voices; lines = score.lines;
      lines.do {|line|
        var streams = line.voiceStreams, ids = Array.newClear(streams.size);
        streams.do {|st, streamidx|
          var init, meter = st.meter, clef = st.clef;
          init = "[\\clef<\"%%\"> \\meter<\"%/%\"> ".format(clef[0],clef[1],meter[0],meter[1]);
          // 1. create & hide all stream objects
          this.netAddr.sendMsg(st.addr, "set", "gmnstream", init);
          this.netAddr.sendMsg(st.addr, "show", 0);
          ids[streamidx] = st.id;
        };
        // 2. create each line as a score expression of multiple streams
        if(ids.size == 1) {
          expr = "&%".format(ids[0]);
        } {
          // multiple streams per line go in a parallel expression
          // see: OSC Commands of INScore, chapter on score expressions.
          expr = "par";
          ids.do {|id| expr = expr + "&%".format(id) };
        };
        expr = "expr(%)".format(expr);
        this.netAddr.sendMsg(line.addr, "set", "gmn", expr); // create line
        // move line to its appropriate location
        this.netAddr.sendMsg(line.addr, "y", (line.score + line.line).linlin(0, numScores + linesPerScore - 2, scorePositionRange[0], scorePositionRange[1]));

        // 4. create event messagers for all changes to streams
        streams.do {|st|
          this.netAddr.sendMsg(st.addr, "watch", "newData", line.addr, "expr", "reeval"); // watch for changes to streams in this line
        };
      };

    };
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
    "INScore: Adding Events: \\gmnWrite \\gmnWriteNote".warn;

    // TODO: Raw gmn stream (just commands to an address)

    // RAW GMN WRITE TO THE SPECIFIED SCORE/VOICE
    Event.addEventType(\gmnWrite, {
      var write;
      var score, voice, stream, dur, gmnDur;
      write = ~write ? "_"; // write a rest by default
      score = ~score ? 1;
      voice = ~voice ? 1;
      if(score.inclusivelyBetween(1, INScore.gmnStreams.size)) {
        // Get current stream for that voice
        score = INScore.gmnStreams.at(score - 1);
        if(voice.inclusivelyBetween(1, score.voices.size)) {
          voice = (score.voices).at(voice - 1);
          stream = voice.streams.at(voice.currStream);
          "write: % '%'".format(stream.addr, write).warn;
          INScore.netAddr.sendMsg(stream.addr, "write", write);
        } {
          "Voice % out of bounds, ignoring".format(voice).error;
        };
      } {
        "Score % out of bounds ignoring".format(score).error;
      }
    });

    // MULTI-SCORE GMN NOTE STREAM
    // USING THIS EVENT ASSUMES YOU HAVE CALLED
    // INScore.init(...) with the score parameters you are
    // interested in using...
    Event.addEventType(\gmnWriteNote, {|serv|
			var write="_", cond, pid, thisEvent;
      var stream, score, voice;
      var dur, amp, rest = false;
      var noteval, numBeatsInEvent;
      var instrument, freq, playNote = false;
      var stacc, accent, pizz, oharm, sharm, gliss, trill;
      dur = ~dur; amp = ~amp;
      stacc = ~stacc; accent = ~accent;
      pizz = ~pizz; oharm = ~oharm; sharm = ~sharm;
      trill = ~trill; gliss = ~gliss;
      if(~synth == \on) {
        instrument = ~instrument; playNote = true;
      };
      if(~freq.notNil) { noteval = ~freq.cpsmidi.midinote };
      if(~note.notNil) { noteval = ~note };
      if(noteval.notNil) {
        var tmptrill=0;
        if(trill == 2) {tmptrill = 1};
        rest = [\rr, \rest, \r].includes(noteval);
        if(rest.not) {
          freq = noteval.f;
        };
        write = noteval.gmn(dur, stacc, accent, pizz, oharm, sharm, tmptrill);
      };
      if(~write.notNil) { write = ~write };


      // score and voice to write to
      score = ~score ? 1;
      voice = ~voice ? 1;
      if(score.inclusivelyBetween(1, INScore.gmnStreams.size)) {
        // Get current stream for that voice
        score = INScore.gmnStreams.at(score - 1);
        if(voice.inclusivelyBetween(1, score.voices.size)) {
          voice = (score.voices).at(voice - 1);


          stream = voice.streams.at(voice.currStream);

          // if stream has written its maximum beats
          // then go to the next stream in this voice
          if(stream.numBeats >= stream.maxBeats) {
            var closeStr = "]";
            // 1. close the old stream and end any modifiers
            if(voice.modifiers.gliss == 1) {
              voice.modifiers.gliss = 0;
              closeStr = "\\glissandoEnd %".format(closeStr);
            };
            this.netAddr.sendMsg(stream.addr, "write", "]");
            stream.open = False;

            // 2. open the next one for writing
            voice.currStream = voice.currStream + 1;
            if(voice.currStream >= voice.numStreams) { voice.currStream = 0 };
            stream = voice.streams.at(voice.currStream);
          };

          // if the stream is not open for writing, prep it
          if(stream.open == False) {
            var init, clef=stream.clef, meter=stream.meter;
            init = "[\\clef<\"%%\"> \\meter<\"%/%\"> ".format(clef[0],clef[1],meter[0],meter[1]);
            this.netAddr.sendMsg(stream.addr, "set", "gmnstream", init);

            stream.open = True;
            stream.numBeats = 0;
          };

          /* not necessary when meter is specified
          // add bar if needed
          if((stream.numBeats != 0).and { (stream.numBeats % stream.meter[0]) == 0 }) {
            write = " \\bar " + write;
          };
          */

          // check modifiers
           // check modifiers
          if((voice.modifiers.gliss == 0).and {gliss==1}) {
              gliss = \start;
              voice.modifiers.gliss = 1;
          } {
            if((voice.modifiers.gliss == 1).and {gliss==0}) {
              gliss = \end;
              voice.modifiers.gliss = 0;
            };
          };
          if((voice.modifiers.trill == 0).and {trill==1}) {
              trill = \start;
              voice.modifiers.trill = 1;
          } {
            if((voice.modifiers.trill == 1).and {trill==0}) {
              trill = \end;
              voice.modifiers.trill = 0;
            };
          };

          switch(gliss,
            \start, { write = " \\glissandoBegin %".format(write) },
            \end, { write = "% \\glissandoEnd ".format(write) },
            { write = write } // default
          );

          switch(trill,
            \start, { write = " \\trillBegin %".format(write) },
            \end, { write = "% \\trillEnd ".format(write) },
            { write = write } // default
          );

          // write whatever there is to write
          "write: % '%'".format(stream.addr, write).warn;
          this.netAddr.sendMsg(stream.addr, "write", write);

          // play a note
          if(playNote.and { rest.not }) {
            (instrument: instrument, freq: freq, amp: amp, dur: dur).play;
          };

          // Calculate beats added by this write
          numBeatsInEvent = dur * stream.meter[1]; // one quarter
          stream.numBeats = stream.numBeats + numBeatsInEvent;
        } {
          "Voice % out of bounds, ignoring".format(voice).error;
        };
      } {
        "Score % out of bounds ignoring".format(score).error;
      }
		}, (freq: \c5.f, note: \c5, write: nil, instrument: nil, amp: 0.5, dur: 1/4, stacc: 0, accent: 0, pizz: 0, sharm: 0, oharm: 0, trill: 0, gliss: 0)); // TODO: implement slurs like glissandi
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

  *say{|msg, dur=0.5, id|
    "SAY '%'".format(msg).warn;
    id = id ? rrand(0, 9999);
    {
      var svg, obj;
      svg = "<svg width='700px' height='200px'><text style=\"font: italic 40px serif; font-size: 50px; fill: red;\" x='10' y='50' textLength='500px'>%</text></svg>".format(msg);
      obj = "/ITL/scene/msgbox" ++ id;
      this.netAddr.sendMsg(obj, "set", "svg", svg);
      dur.wait;
      // fade opacity
      ((18..0) / 18.0).do {|op|
        this.netAddr.sendMsg(obj, "alpha", op);
        0.06.wait;
      };
      this.netAddr.sendMsg(obj, "del");
    }.fork;
  }


  *code{|code, dur=0.4, id|
    id = id ? rrand(0, 9999);
    {
      var svg, obj;
      svg = "<svg width='700px' height='700px'><text style=\"font: monospace; font-size: 30px; fill: grey;\" x='10' y='15' textLength='500px'>%</text></svg>".format(code);
      obj = "/ITL/scene/codebox" ++ id;
      INScore.send(obj, "set", "svg", svg);
      INScore.send(obj, "y", 0.0);
      INScore.send(obj, "x", -0.9);
      dur.wait;
      // fade opacity
      ((10..0) / 10.0).do {|op|
        INScore.send(obj, "alpha", op);
        0.08.wait;
      };
      INScore.send(obj, "del");
    }.fork;
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

/*----------------------------------------
GUIDO MUSIC NOTATION UTILITIES
----------------------------------*/

+ Symbol {
  // convert note symbol to gmn string
  gmn {|dur, stacc=0, accent=0, pizz=0, oharm=0, sharm=0, trill=0|
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
    // additional notations
    if(stacc > 0) { str = "\\stacc(%)".format(str) };
    if(accent > 0) { str = "\\accent(%)".format(str) };
    if(pizz > 0) { str = "\\pizz(%)".format(str) };
    if(oharm > 0) { str = "\\harmonic(%)".format(str) };
    if(sharm > 0) { str = "\\noteFormat<style=\"diamond\">(%)".format(str) };
    if(trill > 0) { str = "\\trill(%)".format(str) };
    ^str;
  }

}


+ SequenceableCollection {
  // convert array of notes to a gmn chord string
  gmn {|dur, stacc=0, accent=0, pizz=0, oharm=0, sharm=0, trill=0|
    var str = "{ % }".format(this.collect(_.gmn(dur)).join(", "));

    // additional notations
    if(stacc > 0) { str = "\\stacc(%)".format(str) };
    if(accent > 0) { str = "\\accent(%)".format(str) };
    if(pizz > 0) { str = "\\pizz(%)".format(str) };
    if(oharm > 0) { str = "\\harmonic(%)".format(str) };
    if(sharm > 0) { str = "\\noteFormat<style=\"diamond\">(%)".format(str) };
    if(trill > 0) { str = "\\trill(%)".format(str) };

    ^str;
  }

  // convert array of notes to a gmn string sequence
  gmnString {|dur, stacc=0, accent=0, pizz=0, oharm=0, sharm=0, trill=0|
    var str = this.collect(_.gmn).join(" ");

    // additional notations
    if(stacc > 0) { str = "\\stacc(%)".format(str) };
    if(accent > 0) { str = "\\accent(%)".format(str) };
    if(pizz > 0) { str = "\\pizz(%)".format(str) };
     if(oharm > 0) { str = "\\harmonic(%)".format(str) };
    if(sharm > 0) { str = "\\noteFormat<style=\"diamond\">(%)".format(str) };
    if(trill > 0) { str = "\\trill(%)".format(str) };

    ^str;
  }
}

+ Rest {
  gmn {|dur|
    if(dur.notNil) { dur = dur * this.value };
    ^(\rr.gmn(dur));
  }
}
