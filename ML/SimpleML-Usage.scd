p = Perceptron.new(2);
p.weights;
p.activationFunc.(0.0);
p.activationFunc.(0.2);
p.predict([0.3, 0.2]);
p.plot; // not implemented yet

( // Some toy data...
d = [[[2.7810836,2.550537003],0],
	[[1.465489372,2.362125076],0],
	[[3.396561688,4.400293529],0],
	[[1.38807019,1.850220317],0],
	[[3.06407232,3.005305973],0],
	[[7.627531214,2.759262235],1],
	[[5.332441248,2.088626775],1],
	[[6.922596716,1.77106367],1],
	[[8.675418651,-0.242068655],1],
	[[7.673756466,3.508563011],1]]
);

p.train(d, 0.1 ,5);


Function

// Visualize a few activation functions
(
v = {|selector, start=(-5), stop=5|
	Array.interpolation(4000, start, stop)
	.collect({|x| x.perform(selector) })
	.plot.domainSpecs_([[start, stop, \lin, 0]]).specs_([[-1.5, 1.5, \lin, 0]])
	.setProperties(\gridOnX, true, \gridOnY, true);
};
);

// Activation functions are important as a choice in dissective practice...
v.(\step);
v.(\sigmoid);
v.(\tanh);
v.(\relu);
v.(\peak);
v.(\valley);


// Waveshaping
{ var sig = SinOsc.ar(10); [sig,sig.step,sig.sigmoid,sig.relu,sig.tanh] }.plot(0.3, minval: -1.2, maxval: 1.2);


// Waveshaping through a Perceptron? It would essentially be linear classification or prediction...
// This may be interesting if the "training data" is also dynamic in some way...

//*****
// Linear Regression
//*****
// some toy data with a linear relationship...
~dp = 100;
~xdata = Array.series(~dp, 0, 1.0 / ~dp) + Array.fill(~dp, {rrand(-0.15, 0.15)});
~ydata = Array.series(~dp, 0, 1.0 / ~dp) + Array.fill(~dp, {rrand(-0.15, 0.15)});
~asPoints = ~xdata.collect {|item,i| [item, ~ydata[i]] };
r = LinearRegression.new;


// Plot the data
b = Rect(0,0, 800, 600);
w = Window.new(bounds: b);
j = JScatterView.new(w, b, ~asPoints).drawAxis_(true).symbolColor_(Color.red).drawMethod_(\fillOval).symbolSize_(5@5);
k = JScatterView.new(w, b, [[0,r.predict(0)],[1,r.predict(1)]]).background_(Color.clear).symbolColor_(Color.blue);
w.front;




r.predict(10);
r.m_term;
r.b_term;

(
Ndef('reg', {arg freq=50,  modhz=8, mult=1.02, fmodhz=1, amp=1.0;
	var sig;
	sig = Saw.ar(Array.geom(30, 1.0, mult) * freq, mul: 0.1).sum;
	sig = BLowPass4.ar(sig, LFTri.ar(SinOsc.ar(fmodhz).range(1,20)).range(500,6700), 0.5);// + (sig*0.1);
	sig = (sig*2).tanh;

	//sig = GVerb.ar(sig, 100, 2, 0.0, 0.0, 25, 1, 0.5, 0.5);
	sig = CombL.ar(sig, 1, 0.05, 2) + sig;
	sig = (sig*2).tanh;
	sig = Pan2.ar(sig,0,SinOsc.ar(modhz)) * amp * 1.0;
	sig = Limiter.ar(LeakDC.ar(sig), 1, 0.001);
}).play(out: 0, numChannels: 2);
);


(
r.callback = {arg model, error;
	var line = [[0, model.predict(0)],[1, model.predict(1)]];
	{
		k.data_(line).refresh(); // update the plot
		Ndef('reg').set(\freq, (model.m_term.abs * 170) + 30, \modhz, (model.b_term.abs.reciprocal+0.01), \mult, 1.0 + (error / 10), \fmodhz, error*3 );

	}.fork(AppClock);
};
);




r.train(~xdata, ~ydata, 3000, 0.005, 0.05);

// reset the model
(
r.m_term = rrand(-1.0, 1.0);
r.b_term = rrand(0.0, 1.0);
);

// change the dataset
(
~dp = 100;
~xdata = Array.series(~dp, 0.0, 1.0 / ~dp) + Array.fill(~dp, {rrand(-0.25, 0.15)});
~ydata = Array.series(~dp, 1.0, -1.0 / ~dp) + Array.fill(~dp, {rrand(-0.15, 0.15)});
//~xdata = Array.fill(~dp, {rrand(0.0, 1.0)});
//~ydata = Array.fill(~dp, {rrand(0.0, 1.0)});
~asPoints = ~xdata.collect {|item,i| [item, ~ydata[i]] };
j.data_(~asPoints).refresh;
);

// TODO:
/*
Be able to change the delaytime in realtime
Be able to change the training data in realtime
Be able to start/stop a training/modelling process...
*/


r.predict(0)
r.predict(1)



0.045*0.039

	var <plot, <background;
	var <>highlightColor, <highlightItem, <>highlightSize, <>isHighlight, <>drawAxis, <>drawValues;
	var <>xAxisName, <>yAxisName;
	var <symbolSize, <>symbolColor, <>drawMethod = \lineTo;
	var <adjustedData, <specX, <specY;


/*
Quarks.install("https://github.com/ambisonictoolkit/PointView")
Quarks.install("https://github.com/ambisonictoolkit/SphericalDesign")
*/

a = Synthe.load
a.gui

p.weights[0]
p.weights[1]
p.weights[2]

// y crossing
x = (0 - p.weights[0]) / p.weights[1];

// x crossing
y = (0 - p.weights[0]) / p.weights[2];


w = PointView.new(bounds: 600@300);
w.points = d.collect {|e| Cartesian.new(e[0][0],e[0][1],0) };
w.points = w.points.add(Cartesian.new(x, 0, 0));
w.points = w.points.add(Cartesian.new(y, 0, 0));
w.connections = [[w.points.size-2,w.points.size-3]]; // make a connection between those last two
w.front;







/* Plotting */
w = Window.new("Plots", 400@200);
p = Plotter.new("Lineplot", 400@200, w).plotMode_(\linear);
l = Plotter.new("Dotplot", 400@200, w).plotMode_(\points);
p.value = (0..100).normalize.collect({|x| x*x; });
l.value = Array.fill(10, { [rand(100),rand(100)] });

l.value = (0..10).normalize;

l.setProperties(\plotColor, Color.blue, \backgroundColor, Color.clear);
w.refresh;


l.specs;
l.domainSpecs;



w.front;

l.data;
l.value = [0.2, 0.5];
l.specs = ControlSpec.new(0.0, 1.0, 'linear', 0.0, 0.0, "");
l.domainSpecs = ControlSpec.new(0.0, 100.0, 'linear', 0.0, 0.0);
l.specs;
l.domainSpecs;


Quarks.gui

(
w = Window.new("Scatter", 600@300);
a = ScatterView(w, 600@300, {|i| [sin(i/50), sin(0.05*i)]}!100, [-1,1].asSpec, [-1,1].asSpec);
w.front;
);



(
var numItems = 630;
w = Window.new(bounds: Rect(40, 40, 800, 150)).front;
a = ScatterView(w, Rect(10, 10, 760, 100), {|i| [sin(i/50), sin(0.05*i)]}!numItems, [-1,1].asSpec, [-1,1].asSpec);
b = ScatterView(w, Rect(10, 10, 760, 100), {|i| [sin(i/21), sin(0.05*i)]}!numItems, [-1,1].asSpec, [-1,1].asSpec);
a.background = Color.gray(0, 0);
a.isHighlight = true;
Slider(w, Rect(10, 120, 760, 10)).action_{|me|
	a.highlightItemRel = me.value;
	w.refresh;
	//a.highlightItem = (me.value*numItems).asInteger.min(numItems-1);
};

a.drawMethod = \lineTo;
a.symbolColor = Color.blue(0.7, 0.3);
a.highlightSize = 10@10;
w.refresh;

)
