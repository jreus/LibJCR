/****************************************
Overload
A livecoding microframework.
*****************************************/
O {
	classvar <over;
	classvar <server, <rootDir;
	classvar <bus;
	classvar <cleanbus, <fxbus, <duckbus;
	classvar <pregroup, <synthgroup, <mixgroup;
	classvar <mixsyn;
	classvar <sampleDir;
	classvar <bufa, <bufd;
	classvar <siga, <sigd;
	classvar <seqa, <seqd;
	classvar <update_codes;
	classvar <samplewin;

	*new {arg key, source, update_code=nil, rate='control';
		var res, override = false;
		if(over.isNil) {
			over = super.new.init;
		};

		// Used to allow updating in Tdef or other stream...
		if(update_code.notNil && (update_codes[key] != update_code)) {
			update_codes[key] = update_code;
			override=true;
		};

		if(source.class == Function || source.isNil) {
			// It's a UGen graph, should be treated as a NodeProxy.
			res = over.addSig(key, source, rate, override);
			^res;
		};
		if(source.class.superclasses.includes(Pattern)) {
			// It's a Pattern, should be treated as a sequence.
			res = over.addSeq(key, source, override);
			^res.next;
		};
		^Error("Not a valid O source: Must be a Pattern or UGen graph function");
	}

	*line {arg key, start=0, end=0, dur=1, update_code;
		^O(key, { Line.kr(start, end, dur) }, update_code);
	}

	*buf {arg idx;
		if(idx.isNumber) {
			^bufa[idx];
		} {
			^bufd[idx];
		};
	}


	init {
		bus = IdentityDictionary.new;
		bufa = Array.newClear;
		bufd = IdentityDictionary.new;
		siga = Array.newClear;
		sigd = IdentityDictionary.new;
		seqa = Array.newClear;
		seqd = IdentityDictionary.new;
		update_codes = IdentityDictionary.new;
	}

	*boot {arg serv=nil, bootfunc=nil, outDevice=nil, root=nil;
		if(over.isNil) {
			over = super.new.init;
		};
		if(serv.notNil) {
			server = serv;
		};
		if(outDevice.notNil) {
			server.options.outDevice_(outDevice);
		};
		if(root.isNil) {
			rootDir = "~/samples".standardizePath;
		} {
			rootDir = root;
		};
		server.waitForBoot {
			bus.put('clean', Bus.audio(server, 2));
			bus.put('fx', Bus.audio(server, 2));
			bus.put('duck', Bus.audio(server, 2));

			pregroup = Group.new(server, 'addToTail');
			synthgroup = Group.new(server, 'addToTail');
			mixgroup = Group.new(server, 'addToTail');

			mixsyn = {arg verbmix=0.5, verbsize=0.9, co_hz=18000, co_rq=1.0, compthresh=0.5, hislope=1.0, dist=1.0, master=1.0;
				var clean, fx, mix, ducker;
				var cratio;
				clean = In.ar(bus['clean'], 2);
				fx = In.ar(bus['fx'], 2);
				ducker = In.ar(bus['duck'], 2);
				fx = BLowPass4.ar(FreeVerb.ar(fx, verbmix, verbsize), co_hz, co_rq);
				mix = fx + clean;
				mix = Compander.ar(mix, ducker, 0.1, 1, hislope, 0.01, 0.01);
				mix = mix + ducker;
				mix = Select.ar((dist == 0.0).asInteger, [ (mix * dist).tanh, Limiter.ar(mix, 1, 0.0001)]);
				mix = LeakDC.ar(mix);
				Out.ar(0, mix * master);
			}.play(target: mixgroup);

			// Load Samples
			over.loadSamples();

			// User defined function
			if(bootfunc.notNil) {
				bootfunc.value;
			};
		};
		^over;
	}

	*mix {arg verbmix=0.5, verbsize=0.9, co_hz=18000, co_rq=1.0, compthresh=0.5, hislope=1.0, dist=1.0, master=1.0;
		mixsyn.set('verbmix', verbmix, 'verbsize', verbsize, 'co_hz', co_hz, 'co_rq', co_rq,
			'compthresh', compthresh, 'hislope', hislope, 'dist', dist, 'master', master);
	}

	// individual access to mix parameters
	*amp {arg val;
		mixsyn.set('master', val);
	}
	*verbsize {arg val;
		mixsyn.set('verbsize', val);
	}
	*verbmix {arg val;
		mixsyn.set('verbmix', val);
	}
	*dist {arg val;
		mixsyn.set('dist', val);
	}



	*play {arg synthdef, args, to='clean';
		args = args ++ 'outBus' ++ bus[to];
		Synth(synthdef, args, synthgroup);
	}

	loadSamples {arg sampledirectory, formatext=".wav";
		if(sampledirectory.isNil) {
			sampleDir = rootDir +/+ "samples/";
		} {
			sampleDir = sampledirectory;
		};
		bufa = Array.newClear;
		bufd = IdentityDictionary.new;
		if(File.exists(sampleDir)) {
			var names, paths;
			paths = (sampleDir +/+ "*" ++ formatext).pathMatch;
			paths.do {arg samplepath;
				var thebuf, name = samplepath.basename.splitext[0];
				thebuf = Buffer.readChannel(server, samplepath, channels: [0]);
				bufd[name.asSymbol] = thebuf;
				bufa = bufa.add(thebuf);
			};
		} {
			Post << "\nERROR: Sample Directory " << sampleDir << " does not exist\n";
		};
	}

	// Human-friendly sample lable list
	*samples {arg xpos=800, ypos=0;
		var it = 0, keys, linechars = 0, blocksize=30, linesize=100;
		if(samplewin.notNil) {
			samplewin.close;
		};
		samplewin = Window.new("Sample Keys", Rect(xpos, ypos, 250, 800));

		keys = bufd.keys;
		keys = keys.asArray.sort;
		keys.do {arg key;
			key = key.asString;
			Post << key.padRight(blocksize, " ");
			StaticText.new(samplewin, Rect(0, it*15, 250, 14)).string_(key);
			linechars = linechars + key.size;
			it = it+1;
			if(it % 4 == 0) {
				linechars = 0;
				Post.nl;
			};
		};
		Post.nl;
		samplewin.front;
	}


	// Manage a collection of nodeproxies for control and audio rate signals.
	addSig {arg name=nil, source=nil, rate='control', override=false;
		var prox;
		if(sigd[name].notNil) {
			prox = sigd[name];
		} {
			prox = NodeProxy.new(server, rate, 1);
			if(source.isNil) { prox.source = { 1.0 } } { prox.source = source; };
			siga = siga.add(prox);
			if(name.notNil) {
				sigd[name] = prox;
			};
		};
		if(override) {
			prox.source = source;
		};
		^prox;
	}

	// Manage a collection of patterns for value sequences.
	// If a nil pattern is provided and a pattern exists for that key, the existing pattern is returned.
	// if a nil pattern is provided and no pattern exists for the key, a Pseq([1], inf) is inserted.
	addSeq {arg key, pattern=nil, override=false;
		var stream;
		if(seqd[key].notNil) {
			stream = seqd[key];
			if(override) {
				seqa.remove(stream);
				stream = pattern.asStream;
				seqd[key] = stream;
				seqa = seqa.add(stream);
			};
		} {
			if(pattern.isNil) {
				pattern = Pseq([1], inf);
			};
			stream = pattern.asStream;
			seqa = seqa.add(stream);
			seqd[key] = stream;
		};
		^stream;
	}


}




