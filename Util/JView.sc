/*****************************************
(C) 2018 Jonathan Reus

Various simple and useful view widgets.

******************************************/

/***
@usage

// Using RadioButtons
(
w = Window.new("R", 200@100);
r = RadioButton.new(w, Rect(100,0,100,100));
t = StaticText(w, Rect(0,0,100,100)).string_("Option").background_(Color.gray(0.1)).stringColor_(Color.gray(0.9));
r.action_({|rad, sel| [rad,sel].postln }).background_(Color.gray(0.1)).traceColor_(Color.gray(0.9));
t.mouseUpAction = {|txt| r.setSelected(r.selected.not); txt.postln };
w.alwaysOnTop_(true);
w.front;
);


// Using RadioSetView
(
w = Window.new("R", 100@100);
r = RadioSetView.new(w, w.bounds);
r.add("Opt1"); r.add("Opt2"); r.add("Opt3");
r.action = {|sv, idx| [sv,idx].postln };
w.alwaysOnTop_(true);
w.front;
);

***/
RadioSetView : CompositeView {
  var <buttons, <>traceColor;
  var <selectedIndex=nil;
  var <>action;

  *new {|parent, bounds|
    ^super.new(parent,bounds).init;
  }

  init {
    this.addFlowLayout;
    buttons = List.new;
    traceColor = Color.gray(0.9);
    this.background = Color.gray(0.1);

  }

  setSelected {|newindex|
    this.selectedIndex_(newindex);
    buttons[newindex].setSelected(true);
  }

  selectedIndex_{|newindex|
    if(newindex >= buttons.size) { "Index % exceeds number of radio buttons".format(newindex).error; ^nil };
    selectedIndex = newindex;
  }

  add {|text|
    var newbut, newtext;
    // add layout here...
    newtext = StaticText(this, 50@20).string_(text).stringColor_(this.traceColor);
    newbut = RadioButton(this, 20@20).background_(this.background).traceColor_(this.traceColor);
    newtext.mouseUpAction = {|txt| newbut.setSelected(newbut.selected.not) };
    newbut.action = {|vw, sel|
      this.buttons.do {|but, idx|
        var txt=but[0], btn=but[1];
        btn.value_(false);
        if(vw == btn) { this.selectedIndex_(idx); if(this.action.notNil) {this.action.(this, idx)} };
      };
      vw.value_(true);
    };
    buttons.add([newtext, newbut]);
  }


}

RadioButton : UserView {
  var <>traceColor, <>inset=0.2;
  var <>selected=false;
  var <>action;

  *new {|parent, bounds|
    ^super.new(parent, bounds).init;
  }

  init {
    this.background = Color.grey(0.1);
    this.traceColor = Color.gray(0.9);
    this.drawFunc_({|vw|
      var width, height, inw, inh;
      width = vw.bounds.width;
      height = vw.bounds.height;

      Pen.use {
        Pen.color = this.background;
        Pen.addRect(vw.bounds);
        Pen.fill;

        Pen.color = this.traceColor;
        Pen.addOval(Rect(0,0,width,height));
        Pen.stroke;

        if(selected) {
          inw = inset*width; inh = inset*height;
          Pen.addOval(Rect(inw,inh,width-(2*inw),height-(2*inh)));
          Pen.fill;
        };
      };
    });

    this.mouseUpAction_({|vw, xpos, ypos| this.setSelected(selected.not) });

    this.refresh;
  }

  value { ^selected }
  value_ {|val|
    selected = val;
    this.refresh;
  }

  setSelected {|val|
    selected = val;
    if(this.action.notNil) { this.action.(this, val) };
    this.refresh;
  }


}

