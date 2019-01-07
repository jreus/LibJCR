/***********************************************
MouseKeyShortcuts

Some convenient mouse & keyboard abstractions for use when
livecoding and in quick need of control over parameters.

(C) 2015 Jonathan Reus / GPLv3


Including:

Mouse step function / quantizer
Mouse funny curves
Keyboard envelope trigger

USE THIS TO CATCH KEY CODES
(
w = Window.new("I catch keystrokes");
w.view.keyDownAction = { arg view, char, modifiers, unicode, keycode;  [char, keycode].postln; };
w.front;
)

**********************************************/


/***************************************************************
Key

@usage


***************************************************************/
Key {
  // simple percussive envelope on press
  *perc {|key, start=1.0, end=0.0, dur=1.0|
    ^KeyState.kr(SSKey.codesByKey[key], end, start, dur);
  }

  // arbitrary envelope trigger
  *env {|key, env|
    ^EnvGen.ar(env, KeyState.kr(SSKey.codesByKey[key], 0, 1, 0));
  }

}

/***************************************************************
Mouse

@usage


***************************************************************/
Mouse {
  // simple percussive envelope on click
  *perc {|start=1.0, end=0.0, dur=1.0|
    ^MouseButton.kr(end, start, dur);
  }

  // arbitrary envelope triggered on click
  *env {|env|
    ^EnvGen.ar(env, MouseButton.kr(0, 1, 0));
  }


  // slow sampling of mouse position
  *slow {|dim=\x, lo=0, hi=1, rate=1, warp=0|
    var ms;
    if(dim==\x)
      { ms = MouseX.kr(lo, hi, warp, 0) }
      { ms = MouseY.kr(lo, hi, warp, 0) };
    ^Latch.kr(ms, Impulse.kr(rate));
  }

  // quantizing of mouse position to step values
  *quant {|dim=\x,lo=0,hi=1,step=0.1,warp=0|
    var ms;
    if(dim==\x)
      { ms = MouseX.kr(lo, hi, warp, 0) }
      { ms = MouseY.kr(lo, hi, warp, 0) };
    ^ms.round(step);
  }

  // quantize mouse position to an arbitrary list of values
  *vals {|dim=\x,arr,warp=0|
    var ms;
    if(dim==\x)
      { ms = MouseX.kr(0, arr.size-1, warp, 0) }
      { ms = MouseY.kr(0, arr.size-1, warp, 0) };
    ^Select.kr(ms.round(1), arr);
  }
}


