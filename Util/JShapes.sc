/*
Envelopes, Scales, Gestures, Waveshaping Functions and other Shaping Utilities
*/

+ Env {

	*halfSine {arg start=0, end=1, curve=4;
		var mid = (start-end) / 2;
		mid = end + mid;
		^Env([start,mid,end],[0.5,0.5],[curve,-1 * curve]);
	}
}

+ Scale {
	// Put custom scales here that should be loaded at startup
	*loadCustom {
		this.all.put(\depth, Scale([0, 0.01, 0.02, 11.2, 36, 45], 6, Tuning([400,5000,6000,200,300,560]/400), "Depth"));
	}

	/*

// INTEGRATE THIS INTO THE RATIOS2 FUNCTION!
	// 12 octaves worth of ratios > midpoint?
y = Array.fill(Scale.major.stepsPerOctave * 6, {arg i;
	Scale.major.degreeToRatio(i, 0);
});

// split at the 3rd octave?
m = 8*3;

z = y[..m];
l = y[(m+1)..];

z = (z.ratiomidi.round - 12).midiratio
l = (l.ratiomidi.round - 12).midiratio
 */

	/*
	@returns an array of ratios of given length from a specific start point and step size.
	*/
	ratios2 {arg start=0, length=7, step=1;
		//degrees.collect(tuning.wrapAt(_)); // this is what's in the semitones method / calculation of semitones...
		//^this.semitones.midiratio // standard return for ratio, converts an interval in semitones into a ratio
		^degrees.collect(tuning.wrapAt(_));
	}
}

// Waveshaping functions
+ UGen {

	step {
		^(this > 0);
	}

	sigmoid {
		^(1 / (1 + exp(-1 * this)))
	}

	// Rectified Linear Unit Function
	relu {
		^ max(this, 0);
	}
}




