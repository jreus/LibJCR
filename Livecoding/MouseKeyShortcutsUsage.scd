/* USE THIS TO CATCH KEY CODES
(
w = Window.new("I catch keystrokes");
w.view.keyDownAction = { arg view, char, modifiers, unicode, keycode;  [char, keycode].postln; };
w.front;
)
*/
s.boot;

(
Ndef('mousekey', {
	var m1,m2,m3,m4,m0;
	var k1,k2,k3;
	var s1,s2,s3,s4,s5,sig,freq=SinOsc.ar(0.01).range(100,300);

	// MORE SOPHISTICATED MOUSE CONTROL
	m0 = MouseButton.kr(0.1,1.0,1.0); // simple linear envelope
	m1 = Latch.kr(MouseY.kr(1.0,10.0), Impulse.kr(5)); // slow sampling of mouse
	m2 = MouseX.kr(70, 74, lag: 0).round(1.0).midicps; // quantizing
	// quantizing of mouse to arbitrary values
	m3 = Select.kr(MouseX.kr(0,7,lag:0).round(1), ([0,2,4,5,6,8,10,11]+60)).midicps;
	//TODO: mouse mapping along arbitrary curves m4

	s1 = SinOsc.ar([freq,freq*1.1]);
	s2 = Saw.ar([freq,freq*1.1]);
	s3 = Pulse.ar([freq*2,2*freq*1.1]);
	s4 = LFTri.ar([freq/2,freq*1.1/2]);
	s5 = Saw.ar(freq*Array.series(100,1.0,0.1), mul: 0.1).sum.tanh!2;

	// Server-Side KEYBOARD CONTROL
	k1 = KeyState.kr(SSKey.codesByKey['w'], 0, 1.0, 1); // simple envelope
	k2 = EnvGen.ar(Env.perc, KeyState.kr(SSKey.codesByKey['q'], 0, 1, 0)); // env trigger
	//KeyEvent

	sig = Mix([s1*k1,s2*k2]);

	//sig = Mix([s1,s2,s3,s4,s5]);
	sig = Splay.ar(sig, 1);
	sig = sig * 0.1;
	//sig = sig * MouseButton.kr(0,1,0.6);

	Limiter.ar(sig, 0.9, 0.001);
}).play(out: 0, numChannels: 2);
);
