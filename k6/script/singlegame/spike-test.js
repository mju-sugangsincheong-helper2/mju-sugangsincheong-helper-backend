import { sleep } from 'k6';
import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { guestLogin } from '../common/login.js';
import { singlegameThresholds } from '../common/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_MAX = parseInt(__ENV.VU_MAX) || 1500;

export const options = {
  stages: [
    { target: VU_MAX, duration: '10s' },  // Rapid ramp-up in 10 seconds
    { target: VU_MAX, duration: '60s' },  // Hold to allow users to play and submit results
    { target: 0, duration: '10s' },       // Ramp-down
  ],
  thresholds: singlegameThresholds,
  noConnectionReuse: true,
};

const gamePayloads = new SharedArray('game-payloads', function () {
  const payloads = [];
  for (let i = 0; i < 100; i++) {
    const totalCourses = [1, 3, 6, 7, 8][Math.floor(Math.random() * 5)];
    const details = [];
    for (let j = 0; j < totalCourses; j++) {
      details.push({
        sequence: j + 1,
        tClickCourse: Math.floor(Math.random() * 400) + 100,
        tClickYes: Math.floor(Math.random() * 300) + 50,
        tClickOk: Math.floor(Math.random() * 300) + 50,
      });
    }
    payloads.push({
      totalCourses,
      isCompleted: true,
      tEnterMain: Math.floor(Math.random() * 500) + 100,
      details,
    });
  }
  return payloads;
});

let token;
let hasPlayed = false;

export default function () {
  if (!token) {
    token = guestLogin();
  }
  if (!token) return;

  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  // Run the game simulation only once per VU session to simulate a single spike completion
  if (!hasPlayed) {
    // 1. Simulate playing duration: sleep for 30 to 50 seconds before submitting
    const playDuration = Math.random() * 20 + 30; // 30s to 50s
    sleep(playDuration);

    // 2. Submit game completion result
    const payload = gamePayloads[Math.floor(Math.random() * gamePayloads.length)];
    http.post(
      `${BASE_URL}/api/v1/singlegame`,
      JSON.stringify(payload),
      { tags: { name: 'POST_singlegame' }, ...params }
    );

    // 3. Query ranking right after completing the game (30% chance)
    if (Math.random() < 0.3) {
      http.get(
        `${BASE_URL}/api/v1/singlegame/rank?totalCourses=${payload.totalCourses}&scope=GLOBAL`,
        { tags: { name: 'GET_rank' }, ...params }
      );
    }

    hasPlayed = true;
  }

  // Once the spike submission is complete, wait out the remaining duration
  sleep(1);
}
