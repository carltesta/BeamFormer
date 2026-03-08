/*
BeamFormer
2026 Carl Testa
A pseudo-Ugen designed to generate spatial audio via BeamForming over linear loudspeaker arrays

Thanks to Ron Kuivila and Bhob Rainey for their help in creating this Class
*/

BeamFormer {

	var <>numSpeakers=64, <>speakerSpacing=0.07, <>speedOfSound=343, <speakerPositions, <speakerDistances, <speakerDelays, <halfDelay, <speakerLength;

	*arrayConf {
		|numSpeakers=64, speakerSpacing=0.07, speedOfSound=343|
		var positions = (0.. numSpeakers - 1) - (numSpeakers/2) + 0.5;
		var distances = positions * speakerSpacing;
		var delays = distances / speedOfSound;
		var length = numSpeakers*speakerSpacing;
		^super.newCopyArgs(numSpeakers, speakerSpacing, speedOfSound,
			positions,
			distances,
			delays,
			delays.last,
			length

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
		delay = DelayC.ar(in, instance.speakerLength*2/speedOfSound, instance.focalDelays(x,y), instance.ampWindow());//max delay time is 2 times the length of the array
		^delay*amp;
	}

	*arDiffuse { |in, x=0, y=6, amp=1, mix=0.5, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343 |
		var delay;
		var instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound );
		var times = ((1-mix) * instance.focalDelays(x,y)) + (mix * instance.diffuseDelays(x,y));
		delay = DelayC.ar(in, instance.speakerLength*2/speedOfSound, times, instance.ampWindow());//max delay time is 2 times the length of the array
		^delay*amp;
	}

	*arSquared { |in, x=0, y=6, amp=1, mix=0.5, numSpeakers=64, speakerSpacing=0.07, speedOfSound=343 |
		var delay;
		var instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound );
		var times = ((1-mix) * instance.focalDelays(x,y)) + (mix * instance.focalDelays(x,y).squared);
		delay = DelayC.ar(in, instance.speakerLength.squared/speedOfSound, times, instance.ampWindow());//max delay time is 2 times the length of the array
		^delay*amp;
	}

	delays { |angle=0|
		var delay = sin(angle) * this.speakerDelays + this.halfDelay; //angle is from -pi/2 to pi/2 when facing an array pi/2 is on the left 0 is in front and -pi/2 is on the right
		^delay;
	}

	focalDelays { |x=0,y=6|

		var distances, delays, minT, maxT;
		distances = ( (x - this.speakerDistances).squared + (y - 0).squared ).sqrt;
		if(y>0,{
		//minT = distances.minItem;
		//delays = (distances-minT)/this.speedOfSound;
		delays = distances/this.speedOfSound;
		},{
		maxT = distances.maxItem;
			delays = (maxT-distances)/this.speedOfSound;
		});
		^delays;
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
		instance = BeamFormer.arrayConf(numSpeakers, speakerSpacing, speedOfSound );
		if(transform.isNil, {
			delayTimes = instance.focalDelays(x,y)
		},{delayTimes = transform.value(instance.focalDelays(x,y))});
		amps = instance.ampWindow();
		^[delayTimes,amps];
	}

}