/******************************************

Macro system within SuperCollider.


2018 Jonathan Reus
jonathanreus.com
info@jonathanreus.com


TODO:
* add auto-numbering of patterns, synthdefs, etc..
* add in-code macros for flow signal / multigesture/envelope drawing/random envelope system
* add code that automatically runs to macros
* add replace-with-side-effects possibility

*******************************************/



/* USAGE: *********************

>>mixer
>>pattern

// disable
Macro.active = false;

********************************/


Macros {
	classvar isActive;
	classvar <dict; // global dictionary of Macros by name
	classvar <byEvalString; // dictionary of Macros by eval string
	classvar <preProcessorFuncs; // Add additional preprocessor functions on top of the Macros system
	// this is useful when you want to add additional SC preprocessor work without overwriting the macro system
	classvar <>parseStr;

	*initClass {
		parseStr = ">>";
		dict = Dictionary.new;
		byEvalString = Dictionary.new;
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
			byEvalString.put(mac.evalString, mac);
		};

		this.active_(true);
	}


	// write macros as an XML file
	*export {|filePath|
		var doc, xml, macro, func, rewrite, txt;

		if(filePath.isNil) {
			filePath = "".resolveRelative +/+ "default.xml";
			while (File.exists(filePath)) {
				filePath = filePath ++ "_2";
			};
		};

		doc = DOMDocument.new;
		doc.appendChild(doc.createProcessingInstruction("xml", "version=\"1.0\""));
		xml = doc.createElement("xml");
		doc.appendChild(xml);

		"EXPORTING AS XML...".postln;

		// for each macro: write macro element to the DOM
		this.dict.keysValuesDo {|key, val, i| val.writeXMLElement(doc, xml) };

		"WRITING TO FILE...".postln;
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
					doc = Document.current;
					pos = doc.selectionStart; // this is where the code was run
					linestart = pos - 1;

					while( {doc.getChar(linestart) != "\n"} )
					{
						linestart = linestart - 1;
					};
					linestart = linestart + 1;

					mac = this.dict.at(name);
					rewrite = mac.rewrite;
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


	// Register a new macro.
	*add {|name, evalString=nil, rewrite=nil, action=nil|
		var newmac = Macro(name,evalString, rewrite, action);
		this.dict.put(name, newmac);
		this.byEvalString.put(evalString, newmac);
		^newmac;
	}
}

Macro {
	var <name, <>evalString, <>rewrite, <>action;

	*new {|name, evalString, rewrite, action|
		^super.newCopyArgs(name, evalString, rewrite, action);
	}

	*newFromXMLElement {|elm|
		var mname, maction, mrewrite, meval;
		mname = elm.getAttribute("name");
		meval = elm.getAttribute("evalString");

		mrewrite = elm.getElement("rewrite");
		if(mrewrite.getFirstChild.notNil)
		{ mrewrite = mrewrite.getFirstChild.getText }
		{ mrewrite = nil };

		maction = elm.getElement("action");
		if(maction.getFirstChild.notNil)
		{ maction = maction.getFirstChild.getText }
		{ maction = nil };

		^this.new(mname, maction, mrewrite, meval);
	}

	/*
	@param owner An instance of DOMDocument
	@param parent The parent node for the element being created, should be DOMNode
	*/
	writeXMLElement {|owner, parent|
		var mac, act, rewrt, txt;
		mac = owner.createElement("macro");
		mac.setAttribute("name", this.name.asString);
		mac.setAttribute("evalString", this.evalString);
		parent.appendChild(mac);
		rewrt = owner.createElement("rewrite");
		rewrt.appendChild(owner.createTextNode(this.rewrite));
		mac.appendChild(rewrt);
		act = owner.createElement("action");
		act.appendChild(owner.createTextNode(this.action.cs));
		mac.appendChild(act);
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