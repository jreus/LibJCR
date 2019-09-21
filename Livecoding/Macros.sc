/*
Macro system within SuperCollider.

2018 Jonathan Reus
jonathanreus.com
info@jonathanreus.com

Developed during ALMAT residency @ IEM Graz
*/


/*
FEATURES / TODOS:
* further develop editor gui
 * edit, save & load functionality
 * a little check/tick for whether a macro is global/local

* allow in-code macros for flow signal / multigesture/envelope drawing/random envelope system
*/

/*
SYSTEM PREPARATION:
Run this to put Macros.load in the startup file.
~fp = Platform.userConfigDir +/+ "startup.scd";
File.exists(~fp);
if(File.exists(~fp).not) { File.use(~fp, "w", {|fp| fp.write("Macros.load;\n") }) };
Document.open(~fp);
*/

/*
USAGE:

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

*/


Macros {
	classvar <isActive;
	classvar <names; // ordered list of Macro names
	classvar <dict; // global dictionary of Macros by name
	classvar <byInputPattern; // dictionary of Macros by eval string
	classvar <preProcessorFunc=nil; // Add additional preprocessor function on top of the Macros system. Useful when you want to add additional SC preprocessor work without overwriting the macro system
	classvar <>prefixStr, <>postfixStr; // prefix/postfix for macro commands
  classvar <>regexp; // basic matching character class
  classvar <>verbose=true;

  // state data storage for macros
  classvar <data;

  // gui & file locations
	classvar <win;
	classvar <macroPath, <defaultLocalMacroPath;

	classvar <globalMacroPath, <onlyGlobal=false;

	*initClass {
		prefixStr = ">>";
    postfixStr = "<<";
    regexp = "[\._\\-\n >< a-zA-Z0-9 \(\) \\+\!\{\}]+";
		names = List.new;
		dict = Dictionary.new;
    data = ();
		byInputPattern = Dictionary.new;
		globalMacroPath = "".resolveRelative +/+ "global_macros.yaml";
	}

  *loadFromFile {|filepath|
    var yaml;

    if(File.exists(filepath).not) {
      "Macros file does not exist at path %".format(filepath).throw;
    };

    yaml = filepath.parseYAMLFile;
    yaml.keysValuesDo {|key, val|
      this.addMacro(Macro.newFromDict(key, val));
    };

  }

  // onlyGlobal==true skips initializing a local config
	*load {|macrodir, onlyglobal=false|
		var xml, filenames, yaml;
    onlyGlobal = onlyglobal;

    "GLOBAL MACRO PATH %".format(globalMacroPath).postln;
		if(File.exists(globalMacroPath).not) {
      this.makeDefaultMacroFile(globalMacroPath)
    };

		yaml = globalMacroPath.parseYAMLFile;
		yaml.keysValuesDo {|key,val| this.addMacro(Macro.newFromDict(key, val)) };

    if(onlyGlobal.not) { // load local macros
      if(macrodir.isNil) {
        macrodir = Document.current.path.dirname +/+ "_macros/";
      };
      macroPath = macrodir;
      defaultLocalMacroPath = macroPath +/+ "local_macros.yaml";
      "LOCAL MACRO PATH: %\n".format(defaultLocalMacroPath).postln;
      if(File.exists(macroPath).not) { File.mkdir(macroPath) };
      if(File.exists(defaultLocalMacroPath).not) {
        File.use(defaultLocalMacroPath, "w",
          {|fp| fp.write("# Local Macros\n")})
      };

      // Load local Macros from Macros directory
      filenames = (macroPath +/+ "*.yaml").pathMatch;
      filenames.do {|path|
        yaml = path.parseYAMLFile;
        if(yaml.notNil) {
          yaml.keysValuesDo {|key,val|
            this.addMacro(Macro.newFromDict(key, val))
          };
        };
      };
    };

		names.sort;
		this.active_(true);
	}

  *at {|name| ^this.dict[name] }

	*makeDefaultMacroFile {|filepath|
		var macros = Dictionary.new;
		File.use(filepath, "w", {|fp|
			var res, dict, arr;
			fp.write("# Global Macros\n");
			res = Dictionary.new;
			res.put('boot', (
        type: "rewrite",
				inputPattern: "boot/([0-9]+)",
				rewritePattern: "(\n
s.options.numInputBusChannels = @1@; s.options.numOutputBusChannels = @1@; s.options.memSize = 65536; s.options.blockSize = 256; s.options.numWireBufs = 512;\n
s.waitForBoot { if(m.notNil) { m.window.close }; m = s.meter; m.window.alwaysOnTop=true; m.window.front; b = m.window.bounds; l = Window.screenBounds.width - b.width; m.window.bounds = Rect(l, 0, b.width, b.height);
 Syn.load; Macros.load;
};\n
);",
				rewriteFunc: nil,
				actionFunc: { ServerOptions.devices.postln }

			));

			res.put('syn', (
        type: "command",
				inputPattern: "syn/([A-Za-z0-9_]+)",
				rewritePattern: "Synth(\\@1@)",
				rewriteFunc: nil,
				actionFunc: nil
			));

			res.put("ndef", (
        type: "rewrite",
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
      res.put("pdef", (
        type: "rewrite",
				inputPattern: "pdef",
				rewriteFunc: {|input,args|
					if(~nMacroPdefs.notNil) { ~nMacroPdefs = ~nMacroPdefs + 1 } { ~nMacroPdefs = 0 };
"
Pdef(\\p%).clear;
(\n
Pdef(\\p%, {arg amp=1.0, pan=0;\n
\tvar sig;\n
\tsig = SinOsc.ar(440) * EnvGen.ar(Env.perc, Impulse.ar(1));\n
});\n
);\n
Ndef(\\p%).play(out:0, numChannels: 1);\n".format(~nMacroPdefs, ~nMacroPdefs, ~nMacroPdefs);
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


  /* Deprecated...
	*asXML {
		var doc = DOMDocument.new, xml;
		doc.appendChild(doc.createProcessingInstruction("xml", "version=\"1.0\""));
		xml = doc.createElement("xml");
		doc.appendChild(xml);
		// for each macro: write macro element to the DOM
		this.dict.keysValuesDo {|key, val, i| val.writeXMLElement(doc, xml) };
		^doc;
	}
  */

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

	addPreProcessorFunc {arg newfunc;
		preProcessorFunc = newfunc;
	}

	*clearPreProcessorFunc {
		preProcessorFunc = nil;
	}

	*active {
		^isActive;
	}

	/*
  Evaluate macros on the given line number in the active Document
  @ln line number in active document

  NOTE: This function is broken, see TODO below!
  */
  *evalLine {|ln|
		var doc,line,pslen,command;
    var pattern, match;
		var mac,rewrite,action;
		doc = Document.current;
		line = doc.getLine(ln);
		pslen = this.prefixStr.size();
    pattern = "^%(%)%$".format(this.prefixStr, this.regexp, this.postfixStr);
    match = line.findRegexp(pattern);

    if(match.size > 0) {
      command = match[1][1];
      mac = dict[command]; // TODO: This only works when the command=input pattern. Should use regexp matching!
      if(mac.notNil) {
        // TODO:: The functionality of rewrite has
        // also changed.. this code no longer works
        rewrite = mac.rewrite(command);
        if(rewrite.notNil) {
          doc.replaceLine(ln, rewrite[0]);
        }
      } {
        "No macro patterns found in '%'".format(line).warn;
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

/* ******* PREPROCESSOR PARSING FUNCTION ********* */
/* ******* PREPROCESSOR PARSING FUNCTION ********* */
/* ******* PREPROCESSOR PARSING FUNCTION ********* */

      prefunc = {|code, interpreter|
				var processedCode = code;
        var stidx, psLen = this.prefixStr.size();
        stidx = code.find(this.prefixStr);
        if(stidx.notNil) { // prefix string found
					var doc, pos, mac, name, linestart;
          var endidx, input;
          endidx = stidx + psLen;
          // read in code for macro parsing
          // (until newline)
          while {(endidx < code.size).and({code[endidx] != $\n})} { endidx = endidx + 1 };
          input = code[(stidx+psLen)..endidx];
          mac = this.dict.at(input); // first try matching against input
          if(mac.isNil) { // then try to match against input pattern (more work)
						var kv, match, it=0, found=false, keyvals = byInputPattern.asAssociations;
						while({found.not && (it < keyvals.size)}) {
							kv = keyvals[it];
              match = kv.key.matchRegexp(input);
              if(match) { mac = kv.value; found=true };
							it = it+1;
						};
					};

					if(mac.notNil) { // matched a macro
            var insertStart, insertSize, rewrite, actionFunc, args, matchSize;
            #rewrite, args = mac.rewrite(input, verbose);

            // Don't do any further sclang parsing
            // all side effects happen in actionFunc
            processedCode = nil;

            switch(mac.type,
              \rewrite, { // insert the rewrite in place of the code
              var doc = Document.current;
              rewrite = code[..(stidx-1)] ++ rewrite ++ code[(endidx+1)..];
              insertStart = doc.selectionStart - 1;
              insertSize = doc.selectionSize;
              if(insertSize == 0) { // a line was evaluated, or a selection using parens
                    // BUG: The parens case is very tricky and requires more complex parsing...
                    // Here I only parse the single-line case..
                  while { (insertStart >= 0).and({doc.getChar(insertStart).at(0) != $\n}) } { insertStart = insertStart-1 };
                  insertStart = insertStart + 1;
                  insertSize = code.size + 1;
                  };

                  doc.string_(rewrite, insertStart, insertSize);
                },
              \command, {  },
              { "Bad macro type %".format(mac.type).error; ^nil }
              )
          } {// if the macro is still nil, no macro was matched
						"No Macro matched %".format(input).error;
          };
				};

				if(preProcessorFunc.notNil) {
					// preprocess the code through additional preProcessor function
						code = preProcessorFunc.value(code, interpreter);
				};

        //"EVALUATING:\n%".format(code).postln;
				processedCode; // send the processed code to SC for parsing
			};
		} {
      // TODO: set the preProcessor to preProcessorFuncs when macros are disabled?
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
  @name unique symbol identifying macro
  @type \command or \rewrite
  @inputPattern pattern matching for macro, if nil will match the name
  @rewrite rewrite pattern or function (potentially with arguments)
	@action side effect action function
  */
	*new {|name, type=\command, inputPattern=nil, rewrite=nil, actionFunc=nil|
		var newmac = Macro(name, type, inputPattern, rewrite, actionFunc);
    "new macro %".format(newmac).postln;
		this.addMacro(newmac);
		^newmac;
	}

  /*
  Add a Macro object...
  */
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
  var <type; /*
  \command ~ invisibly rewrite to executable code before evaluation
  \rewrite ~ replace the input code with another string
  Both execute actionFunc as a side effect
  */

	*new {|name, type, inputPattern, rewrite, actionFunc|
		^super.new.init(name, type, inputPattern, rewrite, actionFunc);
	}

	init {|nm, typ, ip, rw, act|
		actionFunc = act;
    type = typ;
		if(rw.class == Function) { rewriteFunc = rw } { rewritePattern = rw };
    if(nm.isKindOf(Symbol)) { nm = nm.asString; };
    if(nm.isKindOf(String).not) {
      "Macro name must be a string".error;
      ^nil;
    };
    name = nm;
    if(ip.isNil) { ip = name };
    inputPattern = ip;
    ^this;
	}


	/*
	Returns the rewrite string for a given input string
	A rewrite pattern string uses argument placeholders: @1@ @2@ @3@
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

    if(verbose) { "% %  % --> %".format(type, name, input, result).postln };
    ^[result, args];
	}

	/*
	Create new Macro from parsed YAML dictionary
	*/
	*newFromDict {|thename, dict|
		var ipat, typ, repat, refunc, act;

		ipat = dict["inputPattern"];
    typ = dict["type"].asSymbol;
		repat = dict["rewritePattern"];
		refunc = dict["rewriteFunc"];
		act = dict["actionFunc"];

		if(refunc.notNil && (refunc != '')) { refunc = refunc.compile.value };
		if(act.notNil && (act != '')) { act = act.compile.value };

		^this.new(thename, typ, ipat, repat ? refunc, act);
	}


/* Deprecated...
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
  */
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

	// Get the line number where the cursor is positioned.
	cursorLine {
		var ln,lines,thisline,doc, cpos, cnt=0;
    cpos = this.selectionStart + 1;
    lines = this.string.split($\n);
    ln = lines.detectIndex({|item,i| cnt = cnt + item.size + 1; if(cpos <= cnt) { true } { false } }) + 1;
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