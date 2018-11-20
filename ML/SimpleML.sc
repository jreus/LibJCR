/*
Jonathan Reus (c) 2018 GPLv3

Simple Machine Learning Cuts and Preparations
*/


/*
A prediction algorithm based on a linear regression model.
The model assumes input and output data follows a linear relationship:
z = Mx + b

Where the values of M and b are optimized to best fit a set of training data.

This algorithm converts the search problem of finding the best M and b
to a minimization problem of minimizing a given cost function.

NTS: Minimization problems seem to be more efficiently solved than search...

See: https://towardsdatascience.com/introduction-to-machine-learning-algorithms-linear-regression-14c4e325882a
*/
LinearRegression {

	var <>m_term, <>b_term;
	var <>callback; // used to run a function between epochs if desired

	*new {arg initm, initb;
		^super.new.init(initm, initb);
	}

	init {arg initm, initb;
		if(initm.isNil) { initm = rand(1.0) };
		if(initb.isNil) { initb = rand(1.0) };
		m_term = initm;
		b_term = initb;
	}

	/*
	trains the model given a training dataset of data (x) and labels (y)
	@param x_train An array containing the x values of the training dataset
	@param y_train An array containing the y values of the training dataset
	@param epochs How many iterations of gradient descent should be used.
	@stepsize The learning rate, the damping of each step of gradient descent
	@delay a temporal delay between every 10 epochs in seconds, if not 0 then the training is done in a routine
	*/
	train {arg x_train, y_train, epochs=10, stepsize=0.1, delay=0.0;
		var trainfunc = {
			var cost, errors, meanSquareError, y_predict, numsamples;
			numsamples = x_train.size;

			epochs.do {arg epoch;
				y_predict = Array.newClear(numsamples);
				numsamples.do {arg i;
					y_predict[i] = this.predict(x_train[i]);
				};

				// This computes both the cost function (Mean Square Error)
				// and its partial derivatives with respect to m and b
				errors = (y_train - y_predict);
				meanSquareError = (errors**2).sum / numsamples;

				b_term = b_term + (stepsize * 2 * errors.sum / numsamples);
				m_term = m_term + (stepsize * 2 * (errors * x_train).sum / numsamples);

				if(epoch % 10 == 0) {
					"Epoch: %, MSE: %".format(epoch, meanSquareError).postln;
					if(callback.notNil) { callback.(this, meanSquareError) };
					if(delay != 0) { delay.wait };
				};

			}

		};
		if(delay != 0) { trainfunc.fork; } { trainfunc.(); };
	}




	predict {arg x;
		var y;
		y = m_term * x + b_term;
		^y;
	}
}




/*
Basic N-dimensional 2-class linear classifier
activation(sum(x_i) + x_0) = 1|0
where sum goes from 1->N and x_0 is the bias

adapted from: https://machinelearningmastery.com/implement-perceptron-algorithm-scratch-python/
*/
Perceptron {
	var <activationFunc;
	var <weights; // weights[0] is the bias b

	*new {arg numinputs=2, afunc=\step;
		^super.new.init(numinputs, afunc);
	}

	init {arg numinputs, afunc;
		activationFunc = TransferFunction.new(\step);
		weights = Array.fill(numinputs+1, { rand(1.0) });
	}

	numInputs {
		^(weights.size - 1);
	}


	predict {arg inputVector;
		var sum = weights[0];
		inputVector.do {arg x, i;
			sum = sum + (x * weights[i+1]);
		};
		^activationFunc.(sum);
	}

	plot {arg x=1, y=2;
		//Plotter
	}

	/*
	Use stochastic gradient descent to train a basic perceptron classifier
	@input training_dataset  Training dataset examples are pairs [[in1, in2, in3...], out]
	@input l_rate  Learning rate, used to limit the amount each weight is corrected each epoch
	@input num_epochs  Number of epochs to train
	*/
	train {arg training_dataset, l_rate, num_epochs;
		var num_inputs=training_dataset[0][0].size;
		weights = { rand(1.0) }!(num_inputs+1); // randomize weights + bias;
		num_epochs.do {|epoch|
			var sum_error = 0.0;
			training_dataset.do {|example, j|
				var prediction, error;
				prediction = this.predict(example[0]);
				error = example[1] - prediction;
				sum_error = sum_error + error**2;
				weights[0] = weights[0] + (l_rate * error); // adjust bias
				example[0].do {|input,i|
					weights[i+1] = weights[i+1] + (l_rate * error * input);
				};
			};
			"EPOCH %: lrate=%  error=%".format(epoch, l_rate, sum_error).postln;
		};
	}

}


/*

def train_weights(train, l_rate, n_epoch):
weights = [0.0 for i in range(len(train[0]))]
for epoch in range(n_epoch):
sum_error = 0.0
for row in train:
prediction = predict(row, weights)
error = row[-1] - prediction
sum_error += error**2
weights[0] = weights[0] + l_rate * error
for i in range(len(row)-1):
weights[i + 1] = weights[i + 1] + l_rate * error * row[i]
print('>epoch=%d, lrate=%.3f, error=%.3f' % (epoch, l_rate, sum_error))
return weights

*/

/*
Transfer functions for use with SimpleML
*/
TransferFunction {
	classvar <functions;

	*initClass {
		functions = Dictionary.new;

		functions[\step] = {|x| if(x > 0.0) { 1 } { 0 } };
	}

	*new {arg key;
		^functions[key];
	}
}

/*
Utility methods
*/
SimpleML {

}