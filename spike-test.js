import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        // [시나리오 1] 일반 유저 500명의 트래픽 폭격
        traffic_spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },  // 워밍업
                { duration: '30s', target: 500 }, // 500명 폭발!
                { duration: '10s', target: 0 },   // 쿨다운
            ],
            exec: 'normalTraffic', // 아래 normalTraffic 함수 실행
        },
        // [시나리오 2] 정확히 15초 뒤에 지옥불 버튼을 누르는 요원
        chaos_monkey: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            startTime: '15s', // ⭐️ 테스트 시작 후 정확히 15초 뒤에 출동!
            exec: 'triggerBurn', // 아래 triggerBurn 함수 실행
        }
    }
};

// 500명이 무지성으로 호출할 콘서트 조회 API
export function normalTraffic() {
    const url = 'http://localhost:8080/api/v1/concerts';
    const res = http.get(url);
    check(res, { 'is status 200': (r) => r.status === 200 });
    sleep(1);
}

// 15초 뒤에 딱 한 번 실행될 지옥불 트리거
export function triggerBurn() {
    console.log('🔥🔥🔥 [15초 경과] AI 자율 방어 테스트를 위한 강제 부하(/burn) 시작! 🔥🔥🔥');
    // timeout을 길게 줘서 k6가 이 요청 때문에 멈추지 않게 합니다.
    http.get('http://localhost:8080/api/v1/admin/ai/burn', { timeout: '15s' });
}