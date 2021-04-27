/******************************************
Additional Pattern and ProxySpace utlities for livecoding and other things.

2018 Jonathan Reus
jonathanreus.com
*******************************************/

/*
Generates a node proxy representing an envelope, whose bus can be mapped to a parameter. The existance of the pattern value is duration-based. And will hold the final value.

Uses control rate.
*/
PenvProxy {
	*new { arg levels, times, curves, levelScale=1, levelBias=0, timeScale=1;
		var env = Env(levels, times, curves);
		var proxy = NodeProxy.new;
		proxy.source = {
			EnvGen.kr(env, 1, levelScale, levelBias, timeScale, doneAction: 2);
		};
		^proxy;
	}
}

// Replicates the functionality of Array.interpolation
// for patterns
Pinterpolate {
  *new {|size=10, start=0, end=1, repeats=1, offset=0|
    ^Pseq(Array.interpolation(size, start, end), repeats, offset);
  }
}



/*
Array shortcut functions for generating patterns
*/
+ ArrayedCollection {

  pseq {|rep=1, off=0, every=1, restDur=1|
    var arr = this;
    if(every > 1) {
      arr = this.extend(this.size * every, Rest(restDur));
      rep = rep / every;
    };
    ^Pseq(arr,rep,off);
  }

  prand {|rep=inf|
    ^Prand(this, rep);
  }
}

/*
Function additions for Proxy and Patterns
*/
+ Function {
	// shortcut to make a ndef
	ndef {|key|
		^Ndef(key, this);
	}

	// shortcut to make a tdef
	tdef {|key|
		^Tdef(key, this);
	}

	// shortcut to make a Pfunc
	pfunc {|resetFunc|
		^Pfunc(this, resetFunc);
	}
}

