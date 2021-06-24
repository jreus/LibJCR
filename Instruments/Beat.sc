/*
Beatmaker
(c) 2021 Jonathan Reus

*/

/*
@usage

*/

Beat {
	var <clock;
	var <server;
	var <tracksGroup; // should come before the fx/master group but after recordings
	var <playersByName;
	var <isPlaying;

	*new {|beatclock, serv, synthgroup|
		^super.new.init(beatclock, serv, synthgroup);
	}

	init {|beatclock, serv, synthgroup|
		clock = beatclock;
		server = serv;
		tracksGroup = synthgroup;
		this.loadSynthDefsEventTypes();
		isPlaying = false;
		playersByName = Dictionary.new();
	}


	// Load any necessary SynthDefs and Event Types used by Beat
	loadSynthDefsEventTypes {
		var insertSynthName = \trackInsert_2ch;
		if(SynthDescLib.global.synthDescs.at(insertSynthName).isNil) {
			SynthDef(insertSynthName, {|bus, amp=1.0, dest=0|
				Out.ar(dest, In.ar(bus, 2) * amp);
			}).add;
		};
	}

	gui {}

	at {|name|
		^playersByName.at(name);
	}

	mute {|name, muteval=0|
		if(muteval.class === Integer) {
			muteval = (muteval > 0).not;
		};
		playersByName.at(name).mute(muteval);
	}

	muteAll {
		playersByName.do(_.mute(true));
	}

	// Adds a live-sample based sequencer
	liveSequ {|name, pattern, stepd, sdur, outbus, start, end, rate, pan, autoplay=true, act=\none, in=0, timeout=30|
		var seq, smpl, sid;
		sid = "ls_" ++ name;
		"Smpl Name '%'".format(sid).postln;
		// 1. get live sample, or create one if it doesn't already exist...
		smpl = Smpl.at(sid);
		if(smpl.isNil) {
			smpl = Smpl.prepareLiveSample(sid);
		};

		// 2. create or update the sequence
		this.sequ(name, sid, pattern, stepd, sdur, outbus, start, end, rate, pan, autoplay);

		// 3. catch or looprecord
		switch(act,
			\none, {

			},
			\catch, {
				"Catch...".postln;
				Smpl.catch(sid, in, 0.1, 2, timeout);
			},
			\loop, {
				"Continuous Recording...".postln;
				Smpl.catchStart(sid, in, timeout);
			}
		);
	}

	// Adds a basic sample file sequencer
	// Sequence Sample
	samSeq {|name, sampleName, pattern, zeroDegree, sdurscale, outbus, start, end, rate, pan, autoplay=true, stepd=nil|
		if(Smpl.at(sampleName).notNil) {
			var seq;
			seq = playersByName.at(name);
			if(seq.isNil) {
				seq = SampleSequence.new(server, tracksGroup, name, sampleName, pattern, zeroDegree, sdurscale, outbus, start, end, rate, pan, autoplay, stepd);
				playersByName.put(name, seq);
				if(isPlaying && autoplay) {
					"Start Platback with clock %".format(clock).warn;
					seq.play(clock);
				};
			} {
				seq.update(sampleName, pattern, zeroDegree, sdurscale, outbus, start, end, rate, pan, stepd);
			};

		}
	}

	// Adds a basic synth sequencer
	// Sequence Synth
	synSeq {|name, instrumentName, pattern, rootPitch, durScale, outbus, pan, scale, autoplay=true|
		var seq;
		instrumentName = instrumentName.asSymbol;

		seq = playersByName.at(name);
		if(seq.isNil) {
			seq = SynthSequence.new(server, tracksGroup, name, instrumentName, pattern, rootPitch, durScale, outbus, pan, scale, autoplay);
			playersByName.put(name, seq);
			if(isPlaying && autoplay) {
				"Start playback of Synth Sequence with clock %".format(clock).warn;
				seq.play(clock);
			};
		} {
			seq.update(instrumentName, pattern, rootPitch, durScale, outbus, pan, scale);
		};
	}

	play {
		playersByName.keysValuesDo({|id, player|
			player.play(clock);
		});
		isPlaying = true;
	}

	// Stop all patterns
	stop {
		playersByName.keysValuesDo({|id, player|
			player.stop();
		});
		isPlaying = false;
	}

}


EventSequence {
	classvar <ampMap;
	classvar <scaleDegreeMap;

	var <id;
	var <pdefId;
	var <pbindef;
	var <trackBus;
	var <trackFX;
	var <trackAmp=1.0;
	var <muted=false;
	var <patternString;
	var <pitchPatternString;
	var <durationScale=1.0;
	var <eventPanning=0.0;
	var <zeroDegree=0;
	var <zeroOctave=5;
	var <pitchScale;
	var <outBus;
	var <autoPlay;
	var <fixedStepDelta;

	*initClass {
		ampMap = ($0: 0, $1: 0.1, $2: 0.15, $3: 0.2, $4: 0.25, $5: 0.3, $6: 0.35, $7: 0.4, $8: 0.45, $9: 0.5, $a: 0.55, $b: 0.6, $c: 0.7, $d: 0.8, $e: 0.9, $f: 1.0, Rest(): Rest());
		scaleDegreeMap = ($0: 0, $1: 1, $2: 2, $3: 3, $4: 4, $5: 5, $6: 6, $7: 7, $8: 8, $9: 9, $a: 10, $b: 11, $c: 12, $d: 13, $e: 14, $f: 15, Rest(): Rest());
		//this.pr_loadEventTypes;
	}

	*new {|serv, synthgroup, seqid, outbus, autoplay|
		^super.new.initEventSequence(serv, synthgroup, seqid, outbus, autoplay);
	}

	initEventSequence {|serv, synthgroup, seqid, outbus, autoplay|
		id = seqid;
		pdefId = id ++ "_player" ++ rand(100);
		autoPlay = autoplay;
		trackBus = Bus.audio(serv, 2);
		trackFX = Synth(\trackInsert_2ch, [\bus, trackBus, \amp, trackAmp, \dest, outbus], synthgroup, \addToTail);
	}




	//************ PUBLIC API ***************//

	stepd {|stepdval|
		fixedStepDelta = stepdval;
		^this;
	}


	amp {|ampVal|
		trackAmp = ampVal;
		if(muted.not) {
			trackFX.set(\amp, trackAmp);
		};
		^this;
	}

	mute {|muteVal=true|
		muted = muteVal;
		if(muted) {
			trackFX.set(\amp, 0);
		} {
			trackFX.set(\amp, trackAmp);
		};
		^this;
	}

	out {|outbus|
		"New outbus %".format(outbus).warn;
		if(outbus.notNil) {
			"Outbus Set".warn;
			outBus = outbus;
			trackFX.set(\dest, outBus);
		}
		^this;
	}

	pan {|pan|
		if(pan.notNil) {
			eventPanning = pan;
		}
		^this;
	}


	// duration scale : a percentage (float)
	dur {|dur|
		if(dur.notNil) {
			durationScale = dur;
		}
	}


	// musical scale : a scale (Scale)
	scale {|ascale|
		if(ascale.notNil) {
			if(ascale.isKindOf(Scale)) {
				pitchScale = ascale;
			} {
				"'%' is not a Scale".format(ascale).throw;
			};
		}
	}

	// pitch of 0 degree in scale-based notation : a note symbol \c5 or a scale degree
	zeroPitch {|newzeropitch|
		if(newzeropitch.notNil) {
			if(newzeropitch.isKindOf(Symbol)) {
				// note symbol
				var rt, oct;
				#rt, oct = newzeropitch.rootOctave;
				zeroDegree = rt;
				zeroOctave = oct;
			} {
				if(newzeropitch.isKindOf(Number)) {
					// degree set independently of octave
					// could also belong to a non 12TET scale
					zeroDegree = newzeropitch.asInteger;
				} {
					"Invalid zero pitch '%' must be a note symbol or integer degree".format(newzeropitch).throw;
				};
			};
		};
		^this;
	}


	play {|clock|
		pbindef.play(quant: [clock.beatsPerBar, 0, 0, 0], argClock: clock, protoEvent: ());
		^this;
	}

	stop {
		pbindef.stop();
		^this;
	}

}



/*
 ____                        _      ____
/ ___|  __ _ _ __ ___  _ __ | | ___/ ___|  ___  __ _
\___ \ / _` | '_ ` _ \| '_ \| |/ _ \___ \ / _ \/ _` |
 ___) | (_| | | | | | | |_) | |  __/___) |  __/ (_| |
|____/ \__,_|_| |_| |_| .__/|_|\___|____/ \___|\__, |
                      |_|                         |_|
*/
SampleSequence : EventSequence {

	var <sampleName;
	var <sampleStartFrame=0;
	var <sampleEndFrame=nil;
	var <samplePlayRate=1.0;
	var <samplePanning=0.0;



	*new {|serv, synthgroup, seqid, smplname, pattern=nil, zeropitch, sampledurscale, outbus, start, end, rate, pan, autoplay, stepd|
		^super.new(serv, synthgroup, seqid, outbus, autoplay).init(smplname, pattern, zeropitch, sampledurscale, outbus, start, end, rate, pan, stepd);
	}

	init {|smplname, pattern, zeropitch, sampledurscale, outbus, start, end, rate, pan, stepd|
		this.update(smplname, pattern, zeropitch, sampledurscale, outbus, start, end, rate, pan, stepd);
	}

	// Update the currently running sequence
	update {|smplname, pattern, zeropitch, sdurscale, outbus, start, end, rate, pan, stepd|
		var isNewSample, smpl;
		isNewSample = (smplname != sampleName);

		if(isNewSample) {
			smpl = Smpl.at(smplname);
			if(smpl.isNil) {
				"Sample '%' could not be found".format(smplname).throw;
			};
			sampleName = smplname;
			if(start.isNil) {
				start = 0;
			};
			if(end.isNil) {
				end = smpl.numFrames;
				"Sample end frame is nil, setting to %".format(end).warn;
			};
		};

		// rootpitch defaults to \c5 > this is degree: 0 root: 0 octave: 5 in the
		// default note event
		this.zeroPitch(zeropitch);
		this.stepd(stepd);
		this.dur(sdurscale);
		this.out(outbus);
		this.rate(rate);
		this.pan(pan);

		if(start.notNil) {
			sampleStartFrame = start;
		};
		if(end.notNil) {
			sampleEndFrame = end;
		};
		this.setPattern(pattern);
	}




	// SAMPLE PLAYER: Parse text pattern into pbindef
	setPattern {|pattern, type=\AMPLITUDE|
		var parsedRaw, parsedByMeasure, pb, smpl;
		var measureIdx=0, measureStepDelta;

		smpl = Smpl.at(sampleName);
		parsedByMeasure = List.new;
		pattern.stripWhiteSpace().do{|ch|
			var ms;
			ms = parsedByMeasure.at(measureIdx);
			if(ms.isNil) {
				ms = Dictionary.new;
				ms[\stepDelta] = 1;
				ms[\pattern] = "";
				ms[\rawPattern] = "";
				ms[\parsedDegree] = List.new;
				ms[\parsedSmpl] = List.new;
				ms[\parsedDur] = List.new;
				ms[\parsedAmp] = List.new;
				ms[\parsedDelta] = List.new;
				parsedByMeasure.add(ms);
			};

			switch(ch,
				$ , { // new measure
					measureIdx = measureIdx + 1;
				},
				$-, { // rest
					ms[\parsedAmp].add(nil);
					ms[\rawPattern] = ms[\rawPattern] ++ $-;
				},
				{ // event
					if(type == \AMPLITUDE) {
						if(ampMap.includesKey(ch)) {
							ms[\parsedAmp].add(ch);
							ms[\rawPattern] = ms[\rawPattern] ++ ch;
						} {
							"Invalid amp value '%' in pattern '%'".format(ch, pattern).throw;
						}
					};
				}
			);
		};

		// Go through all measures and update stepDeltas
		parsedByMeasure.do {|ms|
			// 1. calculate stepDelta
			if(fixedStepDelta.isNil) {
				ms[\stepDelta] = 1 / ms[\parsedAmp].size;
			} {
				ms[\stepDelta] = fixedStepDelta;
			};

			// 2. update degree, duration & amp values
			ms[\parsedAmp] = ms[\parsedAmp].collect {|val|
				var res;
				// Side effects first
				ms[\parsedDur].add(ms[\stepDelta] * durationScale);
				ms[\parsedDelta].add(ms[\stepDelta]);
				ms[\parsedDegree].add(0);
				ms[\parsedSmpl].add(sampleName);

				if(val.isNil) {
					res = Rest(ms[\stepDelta]);
				} {
					res = ampMap[val];
				};

				res;
			};

		};


		pb = List.new;


		// Build pbind
		pb = Dictionary.new;
		pb.put( \type, \smpl);
		pb.put( \scale, pitchScale );
		pb.put( \root, zeroDegree );
		pb.put( \octave, zeroOctave );
		pb.put( \pan, eventPanning);
		pb.put( \out, trackBus );

		// Create smpl, dur, degree, delta, amp sequences
		pb.put(\smpl, List.new);
		pb.put(\dur, List.new);
		pb.put(\degree, List.new);
		pb.put(\delta, List.new);
		pb.put(\amp, List.new);

		parsedByMeasure.do {|ms|
			pb[\smpl].add( Pseq(ms[\parsedSmpl]) );
			pb[\dur].add( Pseq(ms[\parsedDur]) );
			pb[\degree].add( Pseq(ms[\parsedDegree]) );
			pb[\delta].add( Pseq(ms[\parsedDelta]) );
			pb[\amp].add( Pseq(ms[\parsedAmp]) );
		};

		pb[\dur] = Pseq(pb[\dur], inf);
		pb[\degree] = Pseq(pb[\degree], inf);
		pb[\delta] = Pseq(pb[\delta], inf);
		pb[\amp] = Pseq(pb[\amp], inf);


		// Sample Specific Keys
		pb[\smpl] = Pseq(pb[\smpl], inf);
		pb.put(\start, sampleStartFrame );
		pb.put(\end, sampleEndFrame );
		pb.put(\rootPitch, smpl.rootPitch.f );
		pb.put(\rate, samplePlayRate );


		pbindef = Pbindef(id, *(pb.getPairs));
		patternString = pattern;
		"Successfully built pattern '%'".format(pattern).postln;
	}

	//b.get("gong1").setPitch("  1234 ---5 --6- 7-8-", Scale.major, \c2);
	setPitch {|pattern, scale, root|
		var lastPitch, parsed = List.new;

		if(scale.isNil) {
			scale = Scale.major;
		};

		if(root.isNil) {
			root = \c1;
		};

		lastPitch = 0;
		pattern.replace(" ", "").do{|ch|
			if(ch === $-) {
				parsed.add(lastPitch);
			} {
				parsed.add(ch);
			}
		};

		pbindef.set(\degree, parsed.pseq(inf), \scale, scale, \root, root.f);
		pitchPatternString = pattern;
	}




	//***** PUBLIC API *****//

	// sample playback rate : a float
	rate {|rate|
		if(rate.notNil) {
			samplePlayRate = rate;
		}
		^this;
	}



}


/*
 ____              _   _       ____
/ ___| _   _ _ __ | |_| |__   / ___|  ___  __ _
\___ \| | | | '_ \| __| '_ \  \___ \ / _ \/ _` |
 ___) | |_| | | | | |_| | | |  ___) |  __/ (_| |
|____/ \__, |_| |_|\__|_| |_| |____/ \___|\__, |
       |___/                                 |_|
*/
SynthSequence : EventSequence {

	var <instrumentName;
	var <pitchFactor;


	*new {|serv, synthgroup, seqid, instname, pattern=nil, zeropitch, durscale, outbus, pan, notescale, autoplay|
		^super.new(serv, synthgroup, seqid, outbus, autoplay).init(instname, pattern, zeropitch, durscale, outbus, pan, notescale);
	}

	init {|instname, pattern, zeropitch, durscale, outbus, pan, notescale|
		this.update(instname, pattern, zeropitch, durscale, outbus, pan, notescale);
	}

	// Update the currently running SYNTH sequence
	update {|instname, pattern, zeropitch, durscale, outbus, pan, notescale|
		var isNewInstrument;
		isNewInstrument = (instname != instrumentName);

		if(isNewInstrument) {
			this.instrument(instname);
		};

		// rootpitch defaults to \c5 > this is degree: 0 root: 0 octave: 5 in the
		// default note event
		this.zeroPitch(zeropitch);
		this.dur(durscale);
		this.out(outbus);
		this.scale(notescale);
		this.pan(pan);


		this.setPattern(pattern);
	}


	// INSTRUMENT: Parse text pattern into pbindef
	setPattern {|pattern, type=\SCALEDEGREE|
		var parsedRaw, parsedByMeasure, pb, smpl;
		var measureIdx=0, measureStepDelta;

		parsedByMeasure = List.new;

		pattern.stripWhiteSpace().do{|ch|
			var ms;
			ms = parsedByMeasure.at(measureIdx);
			if(ms.isNil) {
				ms = Dictionary.new;
				ms[\stepDelta] = 1;
				ms[\pattern] = "";
				ms[\rawPattern] = "";
				ms[\parsedDegree] = List.new;
				ms[\parsedDur] = List.new;
				ms[\parsedAmp] = List.new;
				ms[\parsedDelta] = List.new;
				parsedByMeasure.add(ms);
			};

			switch(ch,
				$ , { // new measure
					measureIdx = measureIdx + 1;
				},
				$-, { // rest
					ms[\parsedDegree].add(nil);
					ms[\rawPattern] = ms[\rawPattern] ++ $-;
				},
				{ // event
					if(type == \SCALEDEGREE) {
						if(scaleDegreeMap.includesKey(ch)) {
							ms[\parsedDegree].add(ch);
							ms[\rawPattern] = ms[\rawPattern] ++ ch;
						} {
							"Invalid scale degree '%' in pattern '%'".format(ch, pattern).throw;
						}
					};
				}
			);
		};

		// Go through all measures and update stepDeltas
		parsedByMeasure.do {|ms|
			// 1. calculate stepDelta
			ms[\stepDelta] = 1 / ms[\parsedDegree].size;

			"Parsed degree %".format(ms[\parsedDegree]).warn;

			// 2. update degree, duration & amp values
			ms[\parsedDegree] = ms[\parsedDegree].collect {|val|
				var res;
				// Side effects first
				ms[\parsedDur].add(ms[\stepDelta] * durationScale);
				ms[\parsedDelta].add(ms[\stepDelta]);
				ms[\parsedAmp].add(1.0);

				if(val.isNil) {
					res = Rest(ms[\stepDelta]);
				} {
					res = val.digit;
				};

				res;
			};

			// 3. create a Pbind for this measure
			// TODO: Research is this design pattern useful in any way?
			//    does it allow dynamic shuffling of patterns?
			// see guide on Composing Patterns
			// using EventPatternProxy ~rhythm ~melody example..
			"Build Pbind ... %".format(ms).warn;
			ms[\pbind] = Pbind(
				\degree, Pseq(ms[\parsedDegree], 1),
				\amp, Pseq(ms[\parsedAmp], 1),
				\dur, Pseq(ms[\parsedDur], 1),
			);
		};

		// Build pbind
		pb = Dictionary.new;
		pb.put(\type, \note);
		pb.put(\instrument, instrumentName);

		// Create dur, degree, delta, amp sequences
		pb.put(\dur, List.new);
		pb.put(\degree, List.new);
		pb.put(\delta, List.new);
		pb.put(\amp, List.new);

		parsedByMeasure.do {|ms|
			"Build Pseqs ... %".format(ms).warn;

			pb[\dur].add( Pseq(ms[\parsedDur]) );
			pb[\degree].add( Pseq(ms[\parsedDegree]) );
			pb[\delta].add( Pseq(ms[\parsedDelta]) );
			pb[\amp].add( Pseq(ms[\parsedAmp]) );
		};

		pb[\dur] = Pseq(pb[\dur], inf);
		pb[\degree] = Pseq(pb[\degree], inf);
		pb[\delta] = Pseq(pb[\delta], inf);
		pb[\amp] = Pseq(pb[\amp], inf);

		pb.put( \scale, pitchScale );
		pb.put( \root, zeroDegree );
		pb.put( \octave, zeroOctave );

		pb.put(\pan, eventPanning);
		pb.put(\out, trackBus );

		pbindef = Pbindef(id, *(pb.getPairs));
		patternString = pattern;
		"Successfully built pattern '%'".format(pattern).postln;
	}

	setPitch {|pattern, scale, root|
		var lastPitch, parsed = List.new;

		if(scale.isNil) {
			scale = Scale.major;
		};

		if(root.isNil) {
			root = \c1;
		};

		lastPitch = 0;
		pattern.replace(" ", "").do{|ch|
			if(ch === $-) {
				parsed.add(lastPitch);
			} {
				parsed.add(ch);
			}
		};

		pbindef.set(\degree, parsed.pseq(inf), \scale, scale, \root, root.f);
		pitchPatternString = pattern;
	}

	//***** PUBLIC API *****//

	// set instrument name : a synth symbol in SynthDescLib.global (Symbol)
	instrument {|instname|
		if(SynthDescLib.global.synthDescs.at(instname).notNil) {
			instrumentName = instname;
		} {
			"Could not find synth definition '%'".format(instrumentName).throw;
		};
		^this;
	}





}



// Some helpful utilities for working with beats and samples
// especially for samples that are fragments of larger sample files...
SmplHelper {

	var <sampleList;
	var <samplesByName;

	*new {|slist|
		^super.new.init(slist);
	}

	// Preloads samples in the list and gives some useful functions for playing them...
	// slist is an array of samples in the format
	// [ SampleHelper_ID, Smpl_ID, numChannels, start, end ]
	init {|slist|
		sampleList = slist;
		Smpl.preload(sampleList.collect(_.at(1)));
		samplesByName = Dictionary.new;
		sampleList.do({|it| samplesByName.put(it[0], it) });
	}

	// Make a beatseq using the start and stop positions stored in the sample list
	sq {|beat, name, sidx, pattern, stepdur, sampledur, out, rate, pan|
			var sm;
			if(sidx.class === String) {
				sm = samplesByName.at(sidx);
			} {
				sm = sampleList[sidx];
			};
			beat.samSeq(name, sm[1], pattern, stepdur, sampledur, out, sm[3], sm[4], rate, pan);
	}

	// one shot sample player, uses start and stop frames
	shot {|sidx, out=0, rate=1.0, amp=1.0, co=18000, rq=0.5, pan=0, loops=1|
		var sm;
		if(sidx.class === String) {
			sm = samplesByName.at(sidx);
		} {
			sm = sampleList[sidx];
		};

		if(sm.notNil) {
			Smpl.splay(sm[1], sm[3], sm[4], rate, amp, out, co, rq, pan, loops);
		} {
			"No sample '%' was found".format(sidx).error;
		};
	}

	// get Smpl id for entry
	id {|sidx|
		var sm;
		if(sidx.class === String) {
			sm = samplesByName.at(sidx);
		} {
			sm = sampleList[sidx];
		};

		if(sm.notNil) {
			^sm[1];
		} {
			"No sample '%' was found".format(sidx).error;
		};
	}

	// more flexible wrapper around Smpl.splay
	pl {|sidx, start=0, end=(-1), out=0, rate=1.0, amp=1.0, co=20000, rq=1.0, pan=0.0, loops=1|
		var sm;
		if(sidx.class === String) {
			sm = samplesByName.at(sidx);
		} {
			sm = sampleList[sidx];
		};

		if(sm.notNil) {
			Smpl.splay(sm[1], start, end, rate, amp, out, co, rq, pan, loops);
		} {
			"No sample '%' was found".format(sidx).error;
		};

	}


}
