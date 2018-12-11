// USAGE************



//******************



Macros.load(); // loads default.xml in the same dir as the classfile for Macros

Macros.add("eq4",nil,"sig = BLowShelf.ar(BPeakEQ.ar(BPeakEQ.ar(BHiShelf.ar(sig, 10000, 1, 0), 4000, 1, 0), 1200, 1, 0), 200, 1, 0);");

Macros.postMacros
Macros.dict["eq4"]

sig = BLowShelf.ar(BPeakEQ.ar(BPeakEQ.ar(BHiShelf.ar(sig, 10000, 1, 0), 4000, 1, 0), 1200, 1, 0), 200, 1, 0);

Macros.export;




~fp = "".resolveRelative
Macros.write(~fp);


File.use(p, "w", {|fp| fp.write(JSON.stringify(Macro.rewrites)) });

x["reaper"].postln

File.use(p, "w", {|fp|
	Macro.rewrites.keysValuesDo {|key,val,i|
		fp.write("MAC %\n".format(key));
		fp.write("MAC %\n".format(key));
	};
});

DOMDocument
DOMText
Lag


p = "".resolveRelative +/+ "macros01.xml";

( // Write XML
y = DOMDocument.new;
y.appendChild(y.createProcessingInstruction("xml", "version=\"1.0\""));
x = y.createElement("xml");
y.appendChild(x);

e = y.createElement("macro");
x.appendChild(e);
f = y.createElement("func");
f.setAttribute("numargs", "32");
f.setAttribute("direction", "north");
t = y.createTextNode("{ b.value(); }");
f.appendChild(t);
e.appendChild(f);
z = y.createElement("rewrite");
t = y.createTextNode("function function");
z.appendChild(t);
e.appendChild(z);

e = y.createElement("macro");
x.appendChild(e);
f = y.createElement("func");
f.setAttribute("numargs", "21");
f.setAttribute("direction", "south");
t = y.createTextNode("{ a.function; }");
f.appendChild(t);
e.appendChild(f);
z = y.createElement("rewrite");
z.setAttribute("num", "3");
t = y.createTextNode("a different rewrite");
z.appendChild(t);
e.appendChild(z);

y.format;

a = File.new(p,"w");
y.write(a);
a.close;
);

( // Read XML file from disk and parse it
q = DOMDocument.new(p);
q.format;
x = q.getElementsByTagName("xml").pop;
x.getElementsByTagName("macro").do {|elm,i|
	var func, rewrite;
	"Macro %:".format(i).postln;
	func = elm.getElement("func");
	"FUNC  ".post;
	func.getAttributes.keysValuesDo {|key, attr| "%: %  ".format(key,attr.getNodeValue).post };
	"".postln;
	func.getFirstChild.getText.postln;
	rewrite = elm.getElement("rewrite");
	"Rewrite: \n%".format(rewrite.getFirstChild.getText).postln;
};
);



e = DOMElement.new(y,"macro");
e.setAttribute("numtexts", 10);
g = DOMElement.new(e, "func");
f = y.createComment("Blah Blah Comment");

y.getChildNodes;

e.format

Quarks.gui

quine
Macro.rewrites.pairs

pairsDo