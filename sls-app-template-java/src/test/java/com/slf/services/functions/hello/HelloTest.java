package com.slf.services;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import org.junit.Test;
import com.amazonaws.services.lambda.runtime.Context;
import com.slf.model.Response;
import com.slf.stubs.ContextStub;
import java.util.HashMap;
import java.util.Map;

public class HelloTest {


	private Hello f1 = new Hello();

	public Context context = new ContextStub("jazztest_sls-app-python-function-envid_dev");

	@Test
	public void shouldExecuteRequest() {
		Map<String, Object> input = new HashMap();
		input.put("key", "value");
		Response response = f1.handleRequest(input, context);
		Map<String, String> output = (Map) response.getData();

		assertTrue(output.get("name").equals("value"));

	}
}