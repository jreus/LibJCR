/******************************************

Macro system within SuperCollider.


2018 Jonathan Reus
jonathanreus.com
info@jonathanreus.com


**BUGS**
* export renders XML with multi-line rewrites and actions indented. This is a problem when reloading them especially for rewrites.

********

FEATURES:
* add auto-numbering of patterns, synthdefs, etc..
* add in-code macros for flow signal / multigesture/envelope drawing/random envelope system
* add code that automatically runs to macros
* add replace-with-side-effects possibility

*******************************************/



/* USAGE: *********************

Macros.load(); // loads default.xml in the same dir as the classfile for Macros

Macros.add("eq4",nil,"sig = BLowShelf.ar(BPeakEQ.ar(BPeakEQ.ar(BHiShelf.ar(sig, 10000, 1, 0), 4000, 1, 0), 1200, 1, 0), 200, 1, 0);");

Macros.postMacros;

Macros.export; // should write out current macros to file

Macros.active_(false); // disable macros

// Rewrites are written using an input pattern (regexp) and a rewrite (with placeholders)
// INPUT PATTERN: "s/([A-Za-z0-9_]+)/([A-Za-z0-9_]+)/([A-Za-z0-9_]+)"
// REWRITE: "Synth($_1, $_2, $_3);"
// REWRITE may also be a custom rewrite function that takes the input along with an array of arg values
// REWRITE: {|input,args| "Synth(%,%,%).format(args[0],args[1],args[2])" }

********************************/


Macros {
	classvar isActive;
	classvar <dict; // global dictionary of Macros by name
	classvar <byInputPattern; // dictionary of Macros by eval string
	classvar <preProcessorFuncs; // Add additional preprocessor functions on top of the Macros system
	// this is useful when you want to add additional SC preprocessor work without overwriting the macro system
	classvar <>parseStr;

	*initClass {
		parseStr = ">>";
		dict = Dictionary.new;
		byInputPattern = Dictionary.new;
		preProcessorFuncs = List.new;
	}

	*load {|filePath|
		var xml;
		//this.addDefaultMacros();
		if(filePath.isNil) {
			filePath = "".resolveRelative +/+ "default.xml";
		};

		xml = DOMDocument.new(filePath).getElementsByTagName("xml").pop;
		xml.getElementsByTagName("macro").do {|elm,i| // add new macros from xml file
			var func, rewrite, mac;
			mac = Macro.newFromXMLElement(elm);
			dict.put(mac.name, mac);
			byInputPattern.put(mac.inputPattern, mac);
		};

		this.active_(true);
	}


	*asXML {
		var doc = DOMDocument.new, xml;
		doc.appendChild(doc.createProcessingInstruction("xml", "version=\"1.0\""));
		xml = doc.createElement("xml");
		doc.appendChild(xml);
		// for each macro: write macro element to the DOM
		this.dict.keysValuesDo {|key, val, i| val.writeXMLElement(doc, xml) };
		^doc;
	}

	// export macros to xml file
	*export {|filePath|
		var doc;

		if(filePath.isNil) {
			filePath = "".resolveRelative +/+ "default.xml";
			while ( {File.exists(filePath)} ) {
				filePath = filePath ++ "_2";
			};
		};

		doc = this.asXML;
		"Writing Macros to file... %".format(filePath).postln;
		File.use(filePath, "w", {|fp| doc.write(fp) });
	}

	*addDefaultMacros {
		this.add('boot',
"(
s.options.numInputBusChannels = 10; s.options.numOutputBusChannels = 10;
s.options.memSize = 65536; s.options.blockSize = 256;
s.waitForBoot { Syn.load };
);",
			{
				ServerOptions.devices.postln;
			}
		);
		this.add('reaper',
"
~scRPP = \"\".resolveRelative +/+ \"SuperCollider-Live.RPP\";
(\"open -a Reaper64\"+~scRPP).runInTerminal;
(
s.options.outDevice = \"Soundflower (64ch)\";
s.options.numInputBusChannels = 10; s.options.numOutputBusChannels = 10;
s.options.memSize = 8192 * 2 * 2 * 2; s.options.blockSize = 64 * 2 * 2 * 2;
s.waitForBoot { };
);
"
		);


		this.add('mix',
"(
Ndef('mix', {arg master=1.0;
\tvar mix;
\tvar in1,in2,in3,in4,in5,in6,in7,in8,in9;
\tin1 = In.ar(10, 2); in2 = In.ar(20, 2); in3 = In.ar(30, 2); in4 = In.ar(40, 2); in5 = In.ar(50, 2);
\tin6 = In.ar(60, 2); in7 = In.ar(70, 2); in8 = In.ar(80, 2); in9 = In.ar(90, 2);
\tmix = Mix([in1,in2,in3,in4,in5]);
\tmix = Limiter.ar(mix, 0.999, 0.001);
\tmix = LeakDC.ar(mix);
\tOut.ar(0, mix * master);
}).play;
);"
		);
		this.add('setmix',"Ndef('mix').set('master', 0.0);");
		this.add('pat',
"
Pdef('p01').play(quant: 1);
Pdef('p01').stop;
(
Pdef('p01', Pbind(*[
\tinstrument: 'default',
\tdegree: Pseq([0,2,4,6,8],inf),
\tdur: 1,
\tamp: 1.0,
\tpan: 0,
\tout: 0
]
));
);"
		);
		this.add('pmono',
"
Pdef('p01').play(quant: 1);
Pdef('p01').stop;
(
Pdef('p01', Pmono(*[
\tinstrument: 'default',
\tdegree: Pseq([0,2,4,6,8],inf),
\tdur: 1,
\tamp: 1.0,
\tpan: 0,
\tout: 0
]
));
);"
		);
		this.add('sdef',
"(
SynthDef('xxx', {arg out, amp=1.0, pan=0, freq=440, gate=1, atk=0.1, dec=0.1, sus=0.8, rel=0.1;
	var sig, env;
	env = EnvGen.ar(Env.adsr(atk, dec, sus, rel), gate, doneAction: 2);
	sig = WhiteNoise.ar();
	sig = sig * env;
	Out.ar(out, Pan2.ar(sig, pan, amp));
}).add;
);"
		);
		this.add('ndef',
"(
Ndef('xxx', {arg amp=1.0, pan=0, out=10;
	var sig;
	sig = SinOsc.ar();
	Out.ar(10, Pan2.ar(sig, pan, amp));
}).play;
);"
		);
		this.add('play',
"(
{
	Out.ar(0, SinOsc.ar(1400) * EnvGen.ar(Env.perc, timeScale: 0.1, doneAction: 2));
}.play(s);
);
"
		);
		this.add('eq4',
"
sig = BLowShelf.ar(BPeakEQ.ar(BPeakEQ.ar(BHiShelf.ar(sig, 10000, 1, 0), 4000, 1, 0), 1200, 1, 0), 200, 1, 0);
"
		);

		this.add('start',nil,{arg line;
	var opts,serv;
	serv = Server.default; opts = serv.options;
	opts.numInputBusChannels = 10; opts.numOutputBusChannels = 10;
	opts.memSize = 8192*2*2*2;
	serv.waitForBoot {};
		});
	}

	*addPreProcessorFunc {arg newfunc;
		preProcessorFuncs.add(newfunc);
	}

	*clearPreProcessorFuncs {
		preProcessorFuncs = List.new;
	}

	*active {
		^isActive;
	}

	// Evaluate any macros on the given line number
	*evalLine {arg ln;
		var doc,line,pslen,command;
		var mac,rewrite,action;
		doc = Document.current;
		line = doc.getLine(ln);
		pslen = this.parseStr.size();
		if((pslen==0) || line[0..(pslen-1)] == this.parseStr) { // limit to parse string
			command = line[pslen..].asSymbol;
			mac = dict[command];
			rewrite = mac.rewrite;
			action = mac.action;
			if(rewrite.notNil) {
				doc.replaceLine(ln, rewrite);
			};
			if(action.notNil) {
				action.(line);
			};
		};
	}

	// Evaluate the string as if it were a macro
	*eval {arg string;
		var rewrite, action, name, mac;
		name = string.asSymbol;
		mac = dict[name];
		rewrite = mac.rewrite;
		action = mac.action;
		if(action.notNil) { action.(string) };
		^rewrite;
	}

	*active_ {|val=true|
		var prefunc = nil;
		if(val == true) {
			prefunc = {|code, interpreter|
				var psLen = this.parseStr.size();
				if((psLen==0) || code[0..(psLen-1)] == this.parseStr) {
					var doc, pos, mac, rewrite, action, name, linestart;
					var myscript = code[psLen..];
					name = myscript;
					mac = this.dict.at(name);
					if(mac.isNil) { // try to match against input pattern
						var it=0, found=false, keyvals = byInputPattern.asAssociations;
						keyvals.postln;
						while({found.not && (it < keyvals.size)}) {
							var kv = keyvals[it];
							if(kv.key.matchRegexp(myscript)) { mac = kv.value; found=true };
							it = it+1;
						};
					};

					if(mac.notNil) { // matched a macro

					doc = Document.current;
					pos = doc.selectionStart; // this is where the code was run
					linestart = pos - 1;

					while( {doc.getChar(linestart) != "\n"} )
					{
						linestart = linestart - 1;
					};
					linestart = linestart + 1;

					rewrite = mac.rewrite(myscript);
					action = mac.action;
					if(rewrite.notNil) {
						doc.string_(rewrite, linestart, code.size);
						code = nil; // only return nil if all the commands evaluated successfully
					};
					if(action.notNil) {
						action.value;
						if(rewrite.isNil) { // if there is no rewrite then evaluate after the command
							var scriptlen = myscript.size() + psLen;
							code = code[scriptlen..];
						};
					};
					} {// No Macro Matched
						"Could not evaluate Macro %".format(myscript).postln;
					};
				};

				if(preProcessorFuncs.size() > 0) {
					// preprocess the code through additional preProcessor functions
					preProcessorFuncs.do {arg pre;
						code = pre.value(code, interpreter);
					};

				};
				code; // send the code through to SC without further preprocessing
			};
		};
		thisProcess.interpreter.preProcessor = prefunc;
		isActive = val;
		if(isActive) {  this.postMacros } { "Macros disabled".postln };
		^this;
	}

	*listMacros { ^this.dict.keys }

	*postMacros { "Active Macros: ".post; this.listMacros.do {|m| (" "+m).post}; "".postln }


	/*
	Register a new macro
	@param rewrite Either a rewrite pattern (String) or a custom rewrite function
	*/
	*add {|name, inputPattern=nil, rewrite=nil, action=nil|
		var newmac = Macro(name,inputPattern, rewrite, action);
		this.dict.put(name, newmac);
		this.byInputPattern.put(inputPattern, newmac);
		^newmac;
	}
}

Macro {
	// Potentially can have a rewrite function for complex translations from input code to rewrite
	var <name, <inputPattern, <rewritePattern, <rewriteFunc, <action;

	*new {|name, inputPattern, rewrite, action|
		^super.new.init(name, inputPattern, rewrite, action);
	}

	init {|nm, ip, rw, act|
		"NEW MACRO WITH % %".format(nm,ip).postln;
		name = nm;
		inputPattern = ip;
		action = act;
		if(rw.class == Function) { rewriteFunc = rw } { rewritePattern = rw };
	}

	/*
	Returns the rewrite string for a given input string
	A rewrite pattern string uses argument placeholders: #1# #2# #3#
	*/
	rewrite {|input|
		var placeholders, result, args=[];
		var parsed;
		"REWRITING %: % %".format(name, input, inputPattern).postln;
		parsed = input.findRegexp(inputPattern);
		args = parsed[1..].collect(_[1]);
		"PARSEDARGS % %".format(parsed, args).postln;

		if(args.wrapAt(-1) == "") { args = args[..(args.size-2)] }; // remove empty arg at the end

		if(rewriteFunc.notNil) {// use custom rewrite function
			result = rewriteFunc.(input, args);
		} { // else use rewrite pattern placeholders
			result = rewritePattern;
			placeholders = rewritePattern.findRegexp("#[0-9]#").collect(_[0]); // positions of placeholders
			placeholders.size.do {|i|
				var val;
				val = args[i] ? "nil";
				result = result.replace("#%#".format(i+1), val);
			};
		};
		^result;
	}

	*newFromXMLElement {|elm|
		var thename, theinputpat, repat, refunc, theaction;
		thename = elm.getAttribute("name");
		theinputpat = elm.getAttribute("inputPattern");

		repat = elm.getElement("rewritePattern");
		"rewritePattern %".format(repat).postln;
		if(repat.notNil.and({repat.getFirstChild.notNil}))
		{ repat = repat.getFirstChild.getText }
		{ repat = nil };

		refunc = elm.getElement("rewriteFunc");

		if(refunc.notNil.and({refunc.getFirstChild.notNil}))
		{ refunc = refunc.getFirstChild.getText.compile.value }
		{ refunc = nil };

		theaction = elm.getElement("action");
		"action %".format(theaction).postln;


		if(theaction.notNil.and({theaction.getFirstChild.notNil}))
		{ theaction = theaction.getFirstChild.getText.compile.value }
		{ theaction = nil };

		^this.new(thename, theinputpat, repat ? refunc, theaction);
	}

	/*
	@param owner An instance of DOMDocument
	@param parent The parent node for the element being created, should be DOMNode
	*/
	writeXMLElement {|owner, parent|
		var mac, act, rewrt, txt;
		mac = owner.createElement("macro");
		mac.setAttribute("name", this.name.asString);
		mac.setAttribute("inputPattern", this.inputPattern);
		parent.appendChild(mac);
		rewrt = owner.createElement("rewrite");
		if(this.rewrite.notNil) {
			rewrt.appendChild(owner.createTextNode(this.rewrite));
			mac.appendChild(rewrt);
		};
		if(this.action.notNil) {
			act = owner.createElement("action");
			act.appendChild(owner.createTextNode(this.action.cs));
			mac.appendChild(act);
		};
		^mac;
	}
}




/***
Useful additions to Document

USAGE:
d = Document.current;
d.cursorLine;
d.getLine(51);
d.getLineRange(51);
d.replaceLine(51, "d.cursorLine;");

***/
+ Document {

	// Get the current line where the cursor is positioned.
	cursorLine {
		var ln,lines,thisline,doc;
		doc=this;
		thisline = doc.getSelectedLines(doc.selectionStart()-1, 0); // get the full current line
		thisline = thisline.replace("\n","");
		lines = doc.string.split($\n);
		ln = lines.detectIndex({|item| item == thisline; }) + 1;
		^ln;
	}

	// Get the line at a given line number
	getLine {arg ln;
		^this.string.split($\n).at(ln-1);
	}

	// get the start and end index of a given line, and the length [start,end,length]
	getLineRange {arg ln;
		var start=0, end, lines = this.string.split($\n);
		lines[..(ln-2)].do {arg line;
			start = start + line.size() + 1; // add +1 for the removed newline
		};
		end = start + lines[ln-1].size();
		^[start,end,end-start];
	}

	// replace the contents of a given line with a new string
	replaceLine {arg ln, newstring;
		var range = this.getLineRange(ln);
		this.string_(newstring, range[0], range[2]);
	}
}