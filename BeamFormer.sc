/*
BeamFormer
2026 Carl Testa
A pseudo-Ugen designed to generate spatial audio via BeamForming over linear loudspeaker arrays

Thanks to Ron Kuivila and Bhob Rainey for their help in creating this Class
*/

BeamFormer {

	var <>numSpeakers=64, <>speakerSpacing=0.07, <>speedOfSound=343, <speakerPositions, <speakerDistances, <speakerDelays, <halfDelay, <speakerLength, <lagTime=0.1, <fftSize, <irBuf;

	*arrayConf {
		|numSpeakers=64, speakerSpacing=0.07, speedOfSound=343, fftSize=2048|
		var positions = (0.. numSpeakers - 1) - (numSpeakers/2) + 0.5;
		var distances = positions * speakerSpacing;
		var delays = distances / speedOfSound;
		var length = numSpeakers*speakerSpacing;
		var irPath = "C:/Users/Carl/Documents/sfs-carl/preEQ_25d_IR_7cm_spacing/*";
var irBuffer = SoundFile.collectIntoBuffers(irPath,Server.default);
var bufsize = PartConv.calcBufSize(fftSize, irBuffer[0]);
var irSpectrum = irBuffer.collect({|buf| Buffer.alloc(Server.default, bufsize, 1)});
irSpectrum.do({|buf,n| buf.preparePartConv(irBuffer[n], fftSize)});
		^super.newCopyArgs(numSpeakers, speakerSpacing, speedOfSound,
			positions,
			distances,
			delays,
			delays.last,
			length,
			0.1,//lagTime
			fftSize,
			irSpectrum[0]
		);
	}

	*ar { |in, angle=0, amp=1, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343 |
		var delay;
		var instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound );
		delay = DelayC.ar(in, instance.speakerDelays.abs + instance.halfDelay, instance.delays(angle), instance.ampWindow());
		^delay*amp;
	}

	*arFocal { |in, x=0, y=6, amp=1, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343 |
		var delay;
		var instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound );
		var conv = PartConv.ar(in, instance.fftSize, instance.irBuf);
		delay = DelayC.ar(in, instance.speakerLength*2/speedOfSound, instance.focalDelays(x,y), instance.ampWindow());//max delay time is 2 times the length of the array
		^delay*amp;
	}

	*arFocusDelay { |in, x=0, y=(6.neg), mult=10, amp=1, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343 |
		var delay, duration, conv;

		var instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound );
		duration = (BeamFormer.getFocalDelays(x, y)[1]*mult).maxItem;
		conv = PartConv.ar(in, instance.fftSize, instance.irBuf);
		delay = DelayC.ar(in, duration+1, instance.focalDelays(x,y)*mult, instance.ampWindow());//max delay time is whatever the mult is + 1
		//you have to compensate in your SynthDef to give the synth enough time to complete the delay resolution otherwise, the sound stops before it's done
		^delay*amp;
	}

	*arDiffuse { |in, x=0, y=6, amp=1, mix=0.5, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343 |
		var delay;
		var instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound );
		var times = ((1-mix) * instance.focalDelays(x,y)) + (mix * instance.diffuseDelays(x,y));
		delay = DelayC.ar(in, instance.speakerLength*2/speedOfSound, times, instance.ampWindow());//max delay time is 2 times the length of the array
		^delay*amp;
	}

	/*
	Commenting this out for now, cause I think there is a more flexible way to do some of this stuff
	*arSquared { |in, x=0, y=6, amp=1, mix=0.5, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343 |
		var delay;
		var instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound );
		var times = ((1-mix) * instance.focalDelays(x,y)) + (mix * instance.focalDelays(x,y).squared);
		delay = DelayC.ar(in, instance.speakerLength.squared/speedOfSound, times, instance.ampWindow());//max delay time is 2 times the length of the array
		^delay*amp;
	}
	*/

	*arSpatialMode {
		|in, wavenumber=0.628, amp=1, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343|
		var instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound);
		var weights = instance.spatialMode(wavenumber);
		var mode = in * weights;
		^mode*amp;
	}
/*

	*arSpatialWFS {
		|in, angle=30, amp=1, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343, buffers|
		var window;
		var instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound);
		var delay = DelayC.ar(in, instance.speakerDelays.abs + instance.halfDelay, instance.delays(angle));
		var chain = FFT(Array.fill(numSpeakers, { LocalBuf(512, 1) }, delay));
        chain = PV_MagScale(chain, buffers);
		window = IFFT(chain);
		^window*amp;
	}
	*/

	delays { |angle=0|
		var delay = sin(angle) * this.speakerDelays + this.halfDelay; //angle is from -pi/2 to pi/2 when facing an array pi/2 is on the left 0 is in front and -pi/2 is on the right
		^delay;
	}

	focalDelays { |x=0,y=6|

		var distances, delays, minT, maxT, normaldelays, focusdelays, sig;
		distances = ( (Lag.kr(x,this.lagTime) - this.speakerDistances).squared + (Lag.kr(y,this.lagTime) - 0).squared ).sqrt;
		minT = distances.reduce { |a, b| min(a, b) };
		maxT = distances.reduce { |a, b| max(a, b) };
		normaldelays = (distances-minT)/this.speedOfSound;
		focusdelays = (maxT-distances)/this.speedOfSound;
		sig = Select.kr((y < 0).binaryValue, [normaldelays, focusdelays]);
		^sig;
	}

	*getFocalDelays { |x=0,y=6, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343| //returns normaldelays and focusdelays in an array

		var distances, delays, minT, maxT, normaldelays, focusdelays, sig, instance;
		instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound);
		distances = ( (x - instance.speakerDistances).squared + (y - 0).squared ).sqrt;
		minT = distances.reduce { |a, b| min(a, b) };
		maxT = distances.reduce { |a, b| max(a, b) };
		normaldelays = (distances-minT)/instance.speedOfSound;
		focusdelays = (maxT-distances)/instance.speedOfSound;
		sig = Select.kr((y < 0).binaryValue, [normaldelays, focusdelays]);
		^[normaldelays, focusdelays];
	}

	diffuseDelays { |x=0,y=6|

		var distances = ( (x - this.speakerDistances).squared + (y - 0).squared ).sqrt;
		var delays = distances/this.speedOfSound;
		delays=delays*LFNoise1.kr(0.5).range(-0.05,0.05);
		^delays;
	}

	ampWindow {
		var tukeyTail = cos ( (1.. this.numSpeakers/4)/(this.numSpeakers/4+0.25) -1 * pi) + 1/2;
		var ampWin = tukeyTail ++ 1.dup(this.numSpeakers/2) ++ tukeyTail.reverse;
		^ampWin;
	}

	/*

	makeWeights { | angle = 30, freq = 440|
      var waveNum =      2pi * freq/this.speedOfSound*(sin(angle.degrad));
      var weights = cos(waveNum * this.speakerDistances);
	^weights;
	}

	makeWeightTable { | fftSize=512, angle = 30 |
	var weightTable;
      var fftFreq =     SampleRate.ir/fftSize;
      var fweights = (1..fftSize/2 - 1).collect({ | i |
            this.makeWeights(angle, fftFreq * i)
      });
      ^fweights;
}

	createBuffers {
		var binWeights, weights;
		binWeights = Array.fill(this.numSpeakers, { FloatArray.fill(this.fftSize / 2 - 1, 0) });
		weights = this.makeWeightTable();
		weights.do({|e, c|
			e.do({|scale, i|
				binWeights[i][c] = scale;
			});
		});
		binWeights.do({|e, c|
			speakerBuffers[c].loadCollection(e.abs);
		});
	}
	*/

	spatialMode { | wavenumber=0.628|
	var weights = cos(wavenumber*this.speakerDistances);
	^weights;
	}

	//Helper Functions below
	//The following functions help with testing and or converting values relevant to BeamForming

	*speakerTest {
		|startIndex=0, endIndex=63, waitTime=0.5|
		var sound = {PinkNoise.ar(-6.dbamp)*EnvGen.kr(Env.perc, 1, doneAction: 2)};
		var rout = Routine({
			for(startIndex, endIndex, { |i|
				waitTime.wait;
				sound.play(Server.default, i);
			});
		}).play;
	}

	*plot {
		|x=0, y=6, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343, transform|
		var instance, delayTimes, amps;
		instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound);
		if(transform.isNil, {
			delayTimes = instance.focalDelays(x,y)
		},{delayTimes = transform.value(instance.focalDelays(x,y))});
		amps = instance.ampWindow();
		^[delayTimes,amps];
	}

	*wavelength {
		|frequency=2450, speedOfSound=343|
		var wavelength = speedOfSound/frequency;
		^wavelength;
	}

	*anglularWavenumber {
		|wavelength=0.14, speedOfSound=343|
		var wavenumber = 2pi/wavelength;//k
		^wavenumber;
	}

	*waveNumFromFreqAngle {
		|freq=440, angle=45, speedOfSound=343|
		var wavenumber = ((2pi*freq)/speedOfSound)*(​sin(angle.degrad));
		^wavenumber
	}

	*speedOfSound {
		|celciusTemperature|
		var speedOfSound = 331.3 * (1 + (celciusTemperature/273.15)).sqrt;
		^speedOfSound;
	}

	arrayLength {
		var length = this.speakerDistances.minItem.abs+this.speakerDistances.maxItem+this.speakerSpacing;
		^length;
	}

}