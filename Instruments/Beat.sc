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

	*new {|beatclock, serv, group|
		^super.new.init(beatclock, serv, group);
	}

	init {|beatclock, serv, group|
		clock = beatclock;
		server = serv;
		tracksGroup = group;
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
		playersByName.at(name).setMuted(muteval);
	}

	muteAll {
		playersByName.do(_.setMuted(true));
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
	sequ {|name, sampleName, pattern, stepd, sdur, outbus, start, end, rate, pan, autoplay=true|
		if(Smpl.at(sampleName).notNil) {
			var seq;
			seq = playersByName.at(name);
			if(seq.isNil) {
				seq = SampleSequence.new(server, tracksGroup, name, sampleName, pattern, stepd, sdur, outbus, start, end, rate, pan, autoplay);
				playersByName.put(name, seq);
				if(isPlaying && autoplay) {
					"Start Platback with clock %".format(clock).warn;
					seq.play(clock);
				};
			} {
				seq.update(sampleName, pattern, stepd, sdur, outbus, start, end, rate, pan);
			};

		}
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

SampleSequence {
	classvar <ampMap;

	var <id;
	var <pdefId;
	var <pbindef;
	var <trackBus;
	var <trackFX;
	var <trackAmp=1.0;
	var <muted=false;
	var <sampleName;
	var <patternString;
	var <pitchPatternString;
	var <stepDelta=1.0;
	var <sampleDuration=1.0;
	var <outBus;
	var <autoPlay;
	var <sampleStartFrame=0;
	var <sampleEndFrame=nil;
	var <samplePlayRate=1.0;
	var <samplePanning=0.0;

	*initClass {
		ampMap = ($a: 0, $b: 0.2, $c: 0.4, $d: 0.6, $e: 0.8, $f: 1.0, Rest(): Rest());
		//this.pr_loadEventTypes;
	}


	*new {|serv, tracksgroup, seqid, sname, pattern=nil, stepd, sampledur, outbus, start, end, rate, pan, autoplay|
		^super.new.init(serv, tracksgroup, seqid, sname, pattern, stepd, sampledur, outbus, start, end, rate, pan, autoplay);
	}

	init {|serv, tracksgroup, seqid, sname, pattern, stepd, sdur, outbus, start, end, rate, pan, autoplay=true|
		id = seqid;
		pdefId = id ++ "_player" ++ rand(100);
		autoPlay = autoplay;
		trackBus = Bus.audio(serv, 2);
		"Adding track insert to tail of group %".format(tracksgroup).warn;
		trackFX = Synth(\trackInsert_2ch, [\bus, trackBus, \amp, trackAmp, \dest, outbus], tracksgroup, \addToTail);
		this.update(sname, pattern, stepd, sdur, outbus, start, end, rate, pan);
	}

	// Update the currently running sequence
	update {|sname, pattern, stepd, sdur, outbus, start, end, rate, pan|
		var isNewSample, smpl;
		isNewSample = (sname != sampleName);

		if(isNewSample) {
			smpl = Smpl.at(sname);
			if(smpl.isNil) {
				"Sample '%' could not be found".format(sname).throw;
			};
			sampleName = sname;
			if(start.isNil) {
				start = 0;
			};
			if(end.isNil) {
				end = smpl.numFrames;
				"Sample end frame is nil, setting to %".format(end).warn;
			};
		};

		this.setStepDelta(stepd);
		this.setSampleDuration(sdur);
		this.setOutBus(outbus);
		this.setRate(rate);
		this.setPan(pan);
		if(start.notNil) {
			sampleStartFrame = start;
		};
		if(end.notNil) {
			sampleEndFrame = end;
		};
		this.setPattern(pattern);
	}

	setAmp {|ampVal|
		trackAmp = ampVal;
		if(muted.not) {
			trackFX.set(\amp, trackAmp);
		};
	}

	setMuted {|muteVal=true|
		muted = muteVal;
		if(muted) {
			trackFX.set(\amp, 0);
		} {
			trackFX.set(\amp, trackAmp);
		};
	}

	mute {|muteVal=true|
		this.setMuted(muteVal);
	}


	setSampleDuration {|sampledur|
		if(sampledur.notNil) {
			sampleDuration = sampledur;
		}
	}


	setStepDelta {|stepd|
		if(stepd.notNil) {
			stepDelta = stepd;
		}
	}

	setOutBus {|outbus|
		"New outbus %".format(outbus).warn;
		if(outbus.notNil) {
			"Outbus Set".warn;
			outBus = outbus;
			trackFX.set(\dest, outBus);
		}
	}

	setRate {|rate|
		if(rate.notNil) {
			samplePlayRate = rate;
		}
	}

	setPan {|pan|
		if(pan.notNil) {
			samplePanning = pan;
		}
	}


	// Parse text pattern into pbindef
	setPattern {|pattern|
		var parsed, pb, smpl;

		parsed = List.new;

		pattern.replace(" ", "").do{|ch|

			if(ch === $-) {
				parsed.add(nil);
			} {
				if(ampMap.includesKey(ch)) {
					parsed.add(ch);
				} {
					"Invalid symbol '%' in pattern '%'".format(ch, pattern).throw;
				}
			}
		};

		smpl = Smpl.at(sampleName);
		pb = List.new;
		pb.add(\type); pb.add(\smpl);
		pb.add(\smpl); pb.add(parsed.collect({|st| if(st.isNil) { Rest(stepDelta) } { sampleName } }).pseq(inf));
		pb.add(\dur); pb.add(sampleDuration);
		pb.add(\delta); pb.add(stepDelta);
		pb.add(\amp); pb.add(parsed.collect({|st| if(st.isNil) { 0.0 } { ampMap[st] } }).pseq(inf));
		pb.add(\pan); pb.add(samplePanning);
		pb.add(\freq); pb.add(smpl.rootPitch.f);
		pb.add(\rootPitch); pb.add(smpl.rootPitch.f);
		pb.add(\start); pb.add(sampleStartFrame);
		pb.add(\end); pb.add(sampleEndFrame);
		pb.add(\rate); pb.add( samplePlayRate );
		pb.add(\out); pb.add( trackBus );

		pbindef = Pbindef(id, *pb.asArray);
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


	play {|clock|
		pbindef.play(quant: [clock.beatsPerBar, 0, 0, 0], argClock: clock, protoEvent: ());
	}

	stop {
		pbindef.stop();
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
			beat.sequ(name, sm[1], pattern, stepdur, sampledur, out, sm[3], sm[4], rate, pan);
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
