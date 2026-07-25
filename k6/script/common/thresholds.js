export const singlegameThresholds = {
  http_req_failed: ['rate<0.01'],
  'http_req_duration{name:POST_singlegame}': ['p(95)<150'],
  'http_req_duration{name:GET_rank}': ['p(95)<100'],
  'http_req_duration{name:GET_my_records}': ['p(95)<100'],
  'http_req_duration{name:GET_analysis}': ['p(95)<150'],
  'http_req_duration{name:GuestLogin}': ['p(95)<500'],
};

export const exchangeThresholds = {
  http_req_failed: ['rate<0.01'],
  'http_req_duration{name:GET_main}': ['p(95)<200'],
  'http_req_duration{name:GET_recent_intents}': ['p(95)<200'],
  'http_req_duration{name:POST_intents}': ['p(95)<300'],
  'http_req_duration{name:DELETE_intent}': ['p(95)<300'],
  'http_req_duration{name:GET_messages}': ['p(95)<200'],
  'http_req_duration{name:POST_message}': ['p(95)<300'],
  'http_req_duration{name:PATCH_toggle}': ['p(95)<200'],
  'http_req_duration{name:TestLogin}': ['p(95)<500'],
};

export const multigameThresholds = {
  http_req_failed: ['rate<0.01'],
  'http_req_duration{name:POST_reservations}': ['p(95)<200'],
  'http_req_duration{name:GET_reservations}': ['p(95)<100'],
  'http_req_duration{name:GET_reservations_my}': ['p(95)<100'],
  'http_req_duration{name:POST_waiting_room}': ['p(95)<100'],
  'http_req_duration{name:POST_game_request}': ['p(95)<50'],
  'http_req_duration{name:GET_results}': ['p(95)<200'],
  'http_req_duration{name:GET_results_my}': ['p(95)<100'],
  'http_req_duration{name:GuestLogin}': ['p(95)<500'],
};
