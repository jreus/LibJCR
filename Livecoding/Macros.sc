/******************************************
Macro system within SuperCollider.


2018 Jonathan Reus
jonathanreus.com
info@jonathanreus.com



****************************************
FEATURES TODOS:
* further develop editor gui
 * edit, save & load functionality
 * a little check/tick for whether a macro is global/local

* allow in-code macros for flow signal / multigesture/envelope drawing/random envelope system


*******************************************/

/*
Run this to put Macros.load in the startup file.
~fp = Platform.userConfigDir +/+ "startup.scd";
File.exists(~fp);
if(File.exists(~fp).not) { File.use(~fp, "w", {|fp| fp.write("Macros.load;\n") }) };
Document.open(~fp);

*/



/* USAGE: *********************

Macros.load(); // loads default_macros.yaml in the same dir as the classfile for Macros
// also creates macros local directory

// default macro, creates a server boot command for 2 channels IO
>>boot/2


// add a new macro
Macros.new("eq4","eq4","sig = BLowShelf.ar(BPeakEQ.ar(BPeakEQ.ar(BHiShelf.ar(sig, 10000, 1, 0), 4000, 1, 0), 1200, 1, 0), 200, 1, 0);");

Macros.postMacros;

Macros.gui; // macro editor gui

Macros.export; // should write out current macros to file

Macros.active_(false); // disable macros

// Rewrites are written using an input pattern (regexp) and a rewrite (with placeholders)
// INPUT PATTERN: "s/([A-Za-z0-9_]+)/([A-Za-z0-9_]+)/([A-Za-z0-9_]+)"
// REWRITE: "Synth(@1@, @2@, @3@);"
// REWRITE may also be a custom rewrite function that takes the input along with an array of arg values
// sometimes this is easier than writing a rewrite pattern, but is most useful for accessing system
// resources and environment variables to implement more sophisticated rewriting rules.
// REWRITE: {|input,args| "Synth(%,%,%).format(args[0],args[1],args[2])" }

********************************/


Macros {
	classvar <isActive;
	classvar <names; // ordered list of Macro names
	classvar <dict; // global dictionary of Macros by name
	classvar <byInputPattern; // dictionary of Macros by eval string
	classvar <preProcessorFuncs; // Add additional preprocessor functions on top of the Macros system
	// this is useful when you want to add additional SC preprocessor work without overwriting the macro system
	classvar <>parseStr;
  classvar <>verbose=true;

	classvar <win;
	classvar <macroPath, <defaultLocalMacroPath;

	classvar <globalMacroPath;

	*initClass {
		parseStr = ">>";
		names = List.new;
		dict = Dictionary.new;
		byInputPattern = Dictionary.new;
		preProcessorFuncs = List.new;
		globalMacroPath = "".resolveRelative +/+ "global_macros.yaml";
	}



	*load {|macrodir|
		var xml, filenames, yaml;

		if(macrodir.isNil) { macrodir = Document.current.path.dirname +/+ "macros/" };
		macroPath = macrodir;
		defaultLocalMacroPath = macroPath +/+ "local_macros.yaml";

		"LOCAL MACROS: %\nGLOBAL MACROS %".format(macroPath,globalMacroPath).postln;

		if(File.exists(macroPath).not) { File.mkdir(macroPath) };
		if(File.exists(defaultLocalMacroPath).not) { File.use(defaultLocalMacroPath, "w",
			{|fp| fp.write("# Local Macros\n")}) };
		if(File.exists(globalMacroPath).not) { this.makeDefaultMacroFile(globalMacroPath) };

		// Load Global Macros from default macro file
		yaml = globalMacroPath.parseYAMLFile;
		yaml.keysValuesDo {|key,val| this.addMacro(Macro.newFromDict(key, val)) };

		// Load local Macros from Macros directory
		filenames = (macroPath +/+ "*.yaml").pathMatch;

		filenames.do {|path|
			yaml = path.parseYAMLFile;
			if(yaml.notNil) {
				yaml.keysValuesDo {|key,val| this.addMacro(Macro.newFromDict(key, val)) };
			};
		};


		/*
		// old XML-based macro files
		xml = DOMDocument.new(globalMacroPath).getElementsByTagName("xml").pop;
		xml.getElementsByTagName("macro").do {|elm,i| // add new macros from xml file
		var func, rewrite, mac;
		mac = Macro.newFromXMLElement(elm);
		dict.put(mac.name, mac);
		names.add(mac.name);
		byInputPattern.put(mac.inputPattern, mac);
		};
		*/

		names.sort;
		this.active_(true);
	}


	*makeDefaultMacroFile {|filepath|
		var macros = Dictionary.new;
		File.use(filepath, "w", {|fp|
			var res, dict, arr;
			fp.write("# Global Macros\n");
			res = Dictionary.new;
			res.put('boot', (
				inputPattern: "boot/([0-9]+)",
				rewritePattern: "(\n
s.options.numInputBusChannels = $$1$$; s.options.numOutputBusChannels = $$1$$; s.options.memSize = 65536; s.options.blockSize = 256; s.options.numWireBufs = 512;\n
s.waitForBoot { if(m.notNil) { m.window.close }; m = s.meter; m.window.alwaysOnTop=true; m.window.front; b = m.window.bounds; l = Window.screenBounds.width - b.width; m.window.bounds = Rect(l, 0, b.width, b.height);
 Syn.load;
};\n
);",
				rewriteFunc: nil,
				actionFunc: { ServerOptions.devices.postln }

			));

			res.put('syn', (
				inputPattern: "syn/([A-Za-z0-9_]+)",
				rewritePattern: "Synth(\\@1@)",
				rewriteFunc: nil,
				actionFunc: nil
			));

			res.put("ndef", (
				inputPattern: "ndef",
				rewriteFunc: {|input,args|
					if(~nMacroNdefs.notNil) { ~nMacroNdefs = ~nMacroNdefs + 1 } { ~nMacroNdefs = 0 };
"(\n
Ndef(\\n%, {arg amp=1.0, pan=0;\n
\tvar sig;\n
\tsig = SinOsc.ar(440) * EnvGen.ar(Env.perc, Impulse.ar(1));\n
});\n
);\n
Ndef(\\n%).play(out:0, numChannels: 1);\n".format(~nMacroNdefs, ~nMacroNdefs);
			},
				rewritePattern: nil,
				actionFunc: nil
			));

			fp.write(res.toYAML);
		});
	}

	*gui {
		var width = 200, height = 600, lineheight=20;
		var styler, decorator, childView;
		var macroList, macroName, addBtn, deleteBtn;
		var subView, subStyler;
		if(win.notNil) { win.close };
		win = Window("Macro Editor", (width+10)@500);
		styler = GUIStyler(win);

		// child view inside window, this is where all the gui views sit
		childView = styler.getWindow("Macros", win.view.bounds);
		childView.decorator = FlowLayout(childView.bounds, 5@5);

		macroName = TextField.new(childView, width@lineheight);
		macroName.action = {|field| addBtn.doAction };

		addBtn = styler.getSizableButton(childView, "+", "+", lineheight@lineheight);
		deleteBtn = styler.getSizableButton(childView, "-", "-", lineheight@lineheight);

		macroList = ListView(childView, width@200)
		.items_(names.asArray).value_(nil)
		.stringColor_(Color.white).background_(Color.clear)
		.hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5));

		addBtn.action = {|btn| };

		deleteBtn.action = {|btn| };

		subStyler = GUIStyler(childView); // styler for subwindow
		subView = subStyler.getWindow("Subwindow", width@600); // subwindow

		macroList.action_({ |lv| }); // END SCENELIST ACTION

		win.front;
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
		if(val == true) { // ***** PREPROCESSOR FUNCTION ******
			prefunc = {|code, interpreter|
				var psLen = this.parseStr.size();

				if((psLen==0) || code[0..(psLen-1)] == this.parseStr) {
					var doc, pos, mac, rewrite, name, linestart;
					var myscript = code[psLen..];
					name = myscript;

          mac = this.dict.at(name); // try to just match against macro name
					if(mac.isNil) { // try to match against input pattern
						var it=0, found=false, keyvals = byInputPattern.asAssociations;
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

						rewrite = mac.rewrite(myscript, verbose);
						if(rewrite.notNil) {
							doc.string_(rewrite, linestart, code.size);
							code = nil; // only return nil if all the commands evaluated successfully
						} { // if there is no rewrite, then evaluate any SC code following the macro
              var scriptlen = myscript.size() + psLen;
							code = code[scriptlen..];
            };
					} {// No Macro Matched
						"Could not evaluate Macro %".format(myscript).error;
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

	*postMacros { "Active Macros: ".postln; this.listMacros.do {|m| ("\t"+m).post}; "".postln }

	/*
	Register a new macro
	@param rewrite Either a rewrite pattern (String) or a custom rewrite function
	*/
	*new {|name, inputPattern=nil, rewrite=nil, action=nil|
		var newmac = Macro(name,inputPattern, rewrite, action);
		this.addMacro(newmac);
		^newmac;
	}

	*addMacro {|macro|
		this.dict.put(macro.name, macro);
		this.byInputPattern.put(macro.inputPattern, macro);
		this.names.add(macro.name);
	}
}

Macro {
	// A rewrite can be a simple string with placeholders (i.e. like String.format)
	// or can be a function for complex translations from input code to rewritten code
	var <name, <inputPattern, <rewritePattern, <rewriteFunc, <actionFunc;

	*new {|name, inputPattern, rewrite, action|
		^super.new.init(name, inputPattern, rewrite, action);
	}

	init {|nm, ip, rw, act|
		name = nm;
		inputPattern = ip;
		actionFunc = act;
		if(rw.class == Function) { rewriteFunc = rw } { rewritePattern = rw };
    if(nm.isKindOf(String).not) {
      "Macro name must be a string".error;
      ^nil;
    };
    ^this;
	}

	/*
	Returns the rewrite string for a given input string
	A rewrite pattern string uses argument placeholders: #1# #2# #3#
	*/
	rewrite {|input, verbose=false|
		var placeholders, result, args=[];
		var parsed;
		parsed = input.findRegexp(inputPattern);
		args = parsed[1..].collect(_[1]);

		if(args.wrapAt(-1) == "") { args = args[..(args.size-2)] }; // remove empty arg at the end

		if(rewriteFunc.notNil) {// use custom rewrite function
			result = rewriteFunc.(input, args);
		};
    if(rewritePattern.notNil) { // else use rewrite pattern placeholders
			result = rewritePattern;
			placeholders = rewritePattern.findRegexp("@[0-9]+@").collect(_[0]); // positions of placeholders

			placeholders.size.do {|i|
				var val;
				val = args[i] ? "nil";
				result = result.replace("@%@".format(i+1), val);
			};
		};

    if(actionFunc.notNil) { actionFunc.value(input, args) };
    if(verbose) { "%  % --> %".format(name, input, result).postln };
		^result;
	}

	/*
	Create new Macro from parsed YAML dictionary
	*/
	*newFromDict {|thename, dict|
		var ipat, repat, refunc, act;

		ipat = dict["inputPattern"];
		repat = dict["rewritePattern"];
		refunc = dict["rewriteFunc"];
		act = dict["actionFunc"];

		if(refunc.notNil && (refunc != '')) { refunc = refunc.compile.value };
		if(act.notNil && (act != '')) { act = act.compile.value };

		^this.new(thename, ipat, repat ? refunc, act);
	}

	*newFromXMLElement {|elm|
		var thename, theinputpat, repat, refunc, theaction;
		thename = elm.getAttribute("name");
		theinputpat = elm.getAttribute("inputPattern");

		repat = elm.getElement("rewritePattern");
		if(repat.notNil.and({repat.getFirstChild.notNil}))
		{ repat = repat.getFirstChild.getText }
		{ repat = nil };

		refunc = elm.getElement("rewriteFunc");
		if(refunc.notNil.and({refunc.getFirstChild.notNil}))
		{ refunc = refunc.getFirstChild.getText.compile.value }
		{ refunc = nil };

		theaction = elm.getElement("action");
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
		if(this.rewritePattern.notNil) {
			rewrt.appendChild(owner.createTextNode(this.rewritePattern));
			mac.appendChild(rewrt);
		};
		if(this.rewriteFunc.notNil) {
			rewrt.appendChild(owner.createTextNode(this.rewriteFunc.cs));
			mac.appendChild(rewrt);
		};
		if(this.actionFunc.notNil) {
			act = owner.createElement("action");
			act.appendChild(owner.createTextNode(this.action.cs));
			mac.appendChild(act);
		};
		^mac;
	}
}


/***
YAML Parsing

usage:
f = "".resolveRelative +/+ "YAML-Tests.yaml.scd";
x = f.parseYAMLFile;
x.toYAML;

***/


+ Object {
	toYAML {|depth=0|
		var result = "";
		case { this.isKindOf(Array) } {
			this.do {|val|
				result = result ++ "\n" ++ "-".padLeft(depth+1," ") ++ val.toYAML(depth+1);
			};
		}
		{ this.isKindOf(Dictionary) } {
			this.keysValuesDo {|key,val|
				result = result ++ key.asString.padLeft(depth+key.asString.size, " ") ++ ": ";
				if(val.isKindOf(Dictionary)) { result = result ++ "\n" };
				result = result ++ val.toYAML(depth+1);
			};
		}
		{ this.isKindOf(Function) } { result = result ++ "'%'\n".format(this.cs) }
		{ this.isKindOf(Object) } { result = result ++ "%\n".format(this.cs) };
		^result;
	}
}




/***
Additions to Document

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

  insertAtCursor {arg string;
    this.string_(string, this.selectionStart, 0);
  }
}