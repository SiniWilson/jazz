const assert = require('chai').assert;

const service = require('../handler');

describe('SampleTest', () => {
  it('tests handler', async () => {
    const result = await service.hello({"event": "event"}, {"functionName": "coolFunction-prod"});
    assert(result.statusCode==200);
  });
});
