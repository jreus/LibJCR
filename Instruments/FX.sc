/*
FX Units Livecoda
(c) 2021 Jonathan Reus
*/

/*
@usage

FX Unit Microlanguage
See FX.help()

Examples:

FX.setUnit("fx1", "ws rev")
b.syn("s1", \karp, "0 1 2 3", out: FX.bus("fx1"))


Microland Examples:

ws rev
ws(t1 p20 v2 mx0.8) rev(vt10.5 vs2 er0.7 lo0.2 mi0.5 hi0.4 mx0.5) ws(t2 p5 v1 mx0.5)  lpf(co1200 cv200 rq0.7)
ws(p20 mx0.8) rev(mx0.5) ws(t2 p10 v1 mx0.5) hpf(co1200)
ws(p10 v1 mx0.99) rev(er0.8 mx0.9) ws(t2 p5 v1 mx0.9) lpf(co1200)


*/

FX {
	var <server;
	var <fxgroup; // should be at the end of the server's group structure
	var <units;
	var <cleanbus; // send clean signals here
	var <fxbus; // used internally to send from fxmixproxy to masterSynth
	var <fxmixproxy;
	var <mastersynth; // final signal mixing, limiting & volume stage before audio hardware
	var <>verbose=true;

	*new {|serv, group|
		^super.new.init(serv, group);
	}

	init {|serv, group|
		server = serv;
		// TODO: probably want to create this group automatically here at the end of
		//       the server's node order
		fxgroup = group;
		"Found fx group %".format(fxgroup).warn;
		this.loadSynthDefsEventTypes();


		units = Dictionary.new;
		cleanbus = Bus.audio(server, 2);
		fxbus = Bus.audio(server, 2);
		fxmixproxy = NodeProxy.new(server, \audio, 2);
		fxmixproxy.setGroup(fxgroup);
		fxmixproxy.play(fxbus, 2, fxgroup);
	}

	loadSynthDefsEventTypes {
		SynthDef(\mixMaster, {|gain=1.0, fxbus, cleanbus|
			var mix, fxsig, cleansig;
			cleansig = In.ar(cleanbus, 2);
			fxsig = In.ar(fxbus, 2);
			mix = cleansig+fxsig;
			mix = LeakDC.ar(Limiter.ar(mix, 1.0, 0.001));
			Out.ar(0, mix * gain);
		}).add;
	}

	// Get fx bus by name for feeding into the output of a synthesis process
	bus {|name|
		var unit = units.at(name);
		if(unit.notNil) {
			^unit[\inBus];
		} {
			"No FX unit '%' found".format(name).error;
		}
	}

	// Allocate the data structure for a new FX unit but do not compile the Ndef
	allocUnit {|name, inChannels=2|
		var unit;

		if(mastersynth.isNil) {
			"First FX Unit allocation ~ creating mastersynth".postln;
			mastersynth = Synth(\mixMaster, [
				\fxbus, fxbus,
				\cleanbus, cleanbus
			], fxgroup, \addToTail);

		};

		"NEW UNIT '%' '%'".format(name, inChannels).postln;
		unit = Dictionary.new;
		unit[\name] = name;
		unit[\ndefId] = ("fxunit_"++name).asSymbol;
		unit[\inChannels] = inChannels;
		unit[\inBus] = Bus.audio(server, unit[\inChannels]);
		unit[\inputs] = Dictionary.new; // input processes to this unit
		unit[\gain] = 0.5;
		unit[\code] = Dictionary.new;

		// Code for a simple passthrough
		unit[\code][\head] = "var sig, v1, v2, v3; sig = In.ar(\\inbus.kr(0), %) * \\inamp.kr(1.0, 0.5);".format(unit[\inChannels]);
		unit[\code][\dsp] = List.new;
		unit[\code][\tail] = "sig * \\outamp.kr(1.0);";
		units.put(name, unit);
		^unit;
	}



	// Set the structure of the FXUnit and compile the NDef
	// desc fits a fx unit description microlanguage statement
	setUnit {|name, desc|
		var lex, post, idx, i=0, j=0;
		var dsp = List.new; // this will be populated line by line with the dsp code
		var unit, ndef, ndefid;

		unit = units.at(name);
		if(unit.notNil) {
			lex = desc.stripWhiteSpace.findRegexp("(ws|rev|lpf|hpf|eq)(\\([\ \\.a-z0-9]+\\))?");
			if(verbose) { "setUnit desc lex: %".format(lex).postln };

			idx=0;
			while { idx < lex.size } {
				var argidx, arglex, codebuilder;
				var unit = lex[idx+1][1].asSymbol;
				switch(unit,
					\ws, { // simple waveshaper distortion
						var type=1, pre=20, pre_var=2, mix=0.8, pre_var_type=1, pre_var_rate=1;
						arglex = lex[idx+2][1].findRegexp("(t|p|v|vt|vr|mx)([\\.0-9]+)");
						if(verbose) { "FX Unit Arg Lex: %".format(arglex).postln };
						argidx=0;
						while { argidx < arglex.size } {
							var argname, argval;
							argname = arglex[argidx+1][1].asSymbol;
							argval = arglex[argidx+2][1].asFloat;
							switch(argname,
								\t, { type = argval.asInteger },
								\p, { pre = argval.asFloat },
								\v, { pre_var = argval.asFloat },
								\vt, { pre_var_type = argval.asInteger },
								\vr, { pre_var_rate = argval.asFloat },
								\mx, { mix = argval.asFloat },
								{ LangError("Invalid argument '%' to waveshaper".format(argname)).throw; }
							);
							argidx = argidx+3;
						};
						if([1,2].includes(type)) {
							if([1,2].includes(pre_var_type)) {
								var distfunc, varfunc;
								distfunc = ["tanh", "softclip"][type-1];
								varfunc = ["LFNoise2.ar(%, mul: %)", "SinOsc.ar(%, mul: %)"][pre_var_type-1];
								dsp.add("v1 = Amplitude.ar(sig, 0.001, 0.01);");
								varfunc = varfunc.format(pre_var_rate, pre_var);
								dsp.add("v2 = (sig * (% + %));".format(pre, varfunc));
								dsp.add("sig = (v2.% * v1).madd(%) + sig.madd(1-%);".format(distfunc, mix, mix));
							} { LangError("Invalid preamp variation type % to ws".format(pre_var_type)).throw };
						} { LangError("Invalid waveshaper type '%'".format(type)).throw };
					},
					\lpf, { // lowpass filter
						var co=1200, co_var=200, rq=0.7, co_var_type=1, co_var_rate=0.5;
						arglex = lex[idx+2][1].findRegexp("(co|cv|rq|vt|vr)([\\.0-9]+)");
						argidx=0;
						while { argidx < arglex.size } {
							var argname, argval;
							argname = arglex[argidx+1][1].asSymbol;
							argval = arglex[argidx+2][1].asFloat;
							switch(argname,
								\co, { co = argval.asFloat },
								\cv, { co_var = argval.asFloat },
								\rq, { rq = argval.asFloat },
								\vt, { co_var_type = argval.asInteger },
								\vr, { co_var_rate = argval.asFloat },
								{ LangError("Invalid argument '%' to lpf".format(argname, unit)).throw; }
							);
							argidx = argidx+3;
						};
						if([1,2].includes(co_var_type)) {
							var varfunc;
							varfunc = ["LFNoise2.ar(%, mul: %)", "SinOsc.ar(%, mul: %)"][co_var_type-1];
							varfunc = varfunc.format(co_var_rate, co_var);
							dsp.add("sig = BLowPass4.ar(sig, % + %, %);".format(co, varfunc, rq));

						} { LangError("Invalid cutoff variation type '%'".format(co_var_type)).throw; };

					},
					\hpf, { // hipass filter
						var co=3000, co_var=1000, rq=0.5, co_var_type=1, co_var_rate=1;
						arglex = lex[idx+2][1].findRegexp("(co|cv|rq|vt|vr)([\\.0-9]+)");
						argidx=0;
						while { argidx < arglex.size } {
							var argname, argval;
							argname = arglex[argidx+1][1].asSymbol;
							argval = arglex[argidx+2][1].asFloat;
							switch(argname,
								\co, { co = argval.asFloat },
								\cv, { co_var = argval.asFloat },
								\rq, { rq = argval.asFloat },
								\vt, { co_var_type = argval.asInteger },
								\vr, { co_var_rate = argval.asFloat },
								{ LangError("Invalid argument '%' to hpf".format(argname)).throw; }
							);
							argidx = argidx+3;
						};
						if([1,2].includes(co_var_type)) {
							var varfunc;
							varfunc = ["LFNoise2.ar(%, mul: %)", "SinOsc.ar(%, mul: %)"][co_var_type-1];
							varfunc = varfunc.format(co_var_rate, co_var);
							dsp.add("sig = BHiPass4.ar(sig, % + %, %);".format(co, varfunc, rq));
						} { LangError("Invalid cutoff variation type '%'".format(co_var_type)).throw; };
					},
					\del, { // delay
						dsp.add("sig = CombL.ar(sig, \\dt.kr(0.25), \\dt.kr, \\decay.kr(1.0)) + sig;");
					},
					\rev, { // JP reverb
						var vtime=10.5, vdamp=0.0, vsize=2.0, earlyRef=0.7, lotime=0.2, midtime=0.5, hitime=0.5, mix=0.5;
						arglex = lex[idx+2][1].findRegexp("(vt|da|vs|er|lo|mi|hi|mx)([\\.0-9]+)");
						argidx=0;
						while { argidx < arglex.size } {
							var argname, argval;
							argname = arglex[argidx+1][1].asSymbol;
							argval = arglex[argidx+2][1].asFloat;
							switch(argname,
								\vt, { vtime = argval.asFloat },
								\da, { vdamp = argval.asFloat },
								\vs, { vsize = argval.asFloat },
								\er, { earlyRef = argval.asFloat },
								\lo, { lotime = argval.asFloat },
								\mi, { midtime = argval.asFloat },
								\hi, { hitime = argval.asFloat },
								\mx, { mix = argval.asFloat },
								{ LangError("Invalid argument '%' to verb".format(argname, unit)).throw; }
							);
							argidx = argidx+3;
						};
						dsp.add("v1 = JPverb.ar(sig, %, %, %, %, 0.8, 0.9, %, %, %, 200, 3000);".format(vtime, vdamp, vsize, earlyRef, lotime, midtime, hitime));
						dsp.add("sig = v1.madd(%) + sig.madd(1-%);".format(mix, mix));
					},
					{
						LangError("Invalid fx unit '%'".format(unit)).throw;
					}
				);
				idx=idx+3;
			};
			// 1. END PARSING OF DESC INTO dsp LIST


			// 2. UPDATE DSP DEFINITION IN UNIT
			if(dsp.size > 0) {
				unit[\code][\dsp] = dsp;
			};


			// 3. Construct the Ndef definition and compile it
			ndef = "Ndef('%', { % % % })".format(
				unit[\ndefId],
				unit[\code][\head],
				unit[\code][\dsp].join($ ),
				unit[\code][\tail]
			);

			if(verbose) { "Build Ndef statement: %".format(ndef).postln };
			ndef = ndef.interpret;
			if(verbose) { "Ndef '%' successfully compiled".format(unit[\ndefId]).postln; };
			ndef.set(\inbus, unit[\inBus]);
			unit[\ndef] = ndef;

			// add this Unit's ndef to the fx proxy sourcelist, where it will get mixed with the
			// others and sent to the f.masterOut synth
			// TODO: need to check it doesn't already exist in the sourcelist?
			fxmixproxy.add(unit[\ndef], 0);
			"FX MIX PROXY %".format(fxmixproxy).postln;


		} {
			"FX Unit '%' not found".format(name).error;
		};


	}


	// Microlang help string
	*help {
"
ws: waveshaping distortion
  t=type (1:tanh, 2:softclip)
  p=pre amplification (float)
  v=pre amplification variation (float)
  vt=preamp modulation type (1:LFNoise2 2:SinOsc)
  vr=preamp modulation rate (float)
  mx=wet/dry mix (0-1)

lpf: filter
  co=cutoff hz (float)
  cv=cutoff variation hz (float)
  rq=reciprocal of q (float)
  vt=cutoff modulator type (1: LFNoise2, 2: SinOsc)
  vr=cutoff modulator rate hz (float)

hpf: filter
  co=cutoff hz (float)
  cv=cutoff variation hz (float)
  rq=reciprocal of q (float)
  vt=cutoff modulator type (1: LFNoise2, 2: SinOsc)
  vr=cutoff modulator rate hz (float)

del: delay
  no params yet

rev: reverb JPverb
  vt=reverb time in seconds, does not effect early reflections
  da=hf damping as verb decays (0-1.0) - 0 is no damping
  vs=reverb size, scales size of delay lines (0.5 - 5) - values below 1 sound metallic
  er=early reflection shape (0-1.0) 0.707 = smooth exp decay / lower value = slower buildup
  lo=reverb time multiplier in low frequencies (0-1.0)
  mi=reverb time multiplier in mid frequencies (0-1.0)
  hi=reverb time multiplier in hi frequencies (0-1.0)
  mx=wet/dry mix (0-1)

gn: gain
".postln;
	}




}


// TODO: At some point maybe useful to build out this abstraction..
FXUnit {


}

