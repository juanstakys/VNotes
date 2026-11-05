import {test} from 'node:test';
import assert from 'node:assert/strict';
import {thresholdFromSlider, sliderFromThreshold, validThreshold, levelPosition} from '../sensitivity.js';
import {EnergyVAD} from '../vad.js';
test('sensitivity increases rightward across four orders of magnitude',()=>{
 assert.equal(thresholdFromSlider(0),.1);assert.equal(thresholdFromSlider(100),.00001);
 assert.ok(thresholdFromSlider(100)<.006/500);
 for(const t of [.006,.01,.02,.04,.00001,.1])assert.ok(Math.abs(thresholdFromSlider(sliderFromThreshold(t))-t)<1e-10);
 assert.ok(validThreshold(.00002));assert.equal(validThreshold(NaN),false);assert.equal(validThreshold(0),false);assert.equal(validThreshold('0.01'),false);
});
test('very quiet speech can arm energy detection at the highest sensitivity',()=>{
 const vad=new EnergyVAD({sampleRate:16000,threshold:thresholdFromSlider(100)});
 vad.push(.00002,4800);assert.ok(vad.heard);assert.equal(vad.push(0,32000),true);
 assert.ok(levelPosition(.00002)>levelPosition(.00001));assert.equal(levelPosition(0),0);assert.equal(levelPosition(1),100);
});
