/******************************************
Language-side Temporal Gestures

(C) 2019 Jonathan Reus
GPL 3

*******************************************/



/*--------------------------------------------------------------

@usage

Gest.lin({|v| v.postln }, dur: 3);
Gest.env({|v| v.postln }, Env.perc(1.0,1.0));

________________________________________________________________*/

Gest {

  *lin {|cb, from=0.0, to=1.0, dur=1, res=100|
    ^{
      Array.interpolation(res, from, to).do {|i|
        cb.value(i);
        (dur/res).wait;
      };
    }.fork(AppClock);
  }

  *env {|cb, env, timeStretch=1.0, res=100|
    ^{
      var dur = env.totalDuration * timeStretch;
      env.asSignal(res).do {|i|
      cb.value(i);
      (dur/res).wait;
    }}.fork(AppClock);
  }

}


