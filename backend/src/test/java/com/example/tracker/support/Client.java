package com.example.tracker.support;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A browser-like MockMvc client: keeps cookies and echoes the CSRF cookie in X-XSRF-TOKEN. */
public class Client {

  private final MockMvc mvc;
  private final Map<String, Cookie> cookies = new LinkedHashMap<>();

  public Client(MockMvc mvc) {
    this.mvc = mvc;
  }

  public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
    if (!cookies.containsKey("XSRF-TOKEN")) {
      fetchCsrf();
    }
    if (!cookies.isEmpty()) {
      request.cookie(cookies.values().toArray(Cookie[]::new));
    }
    Cookie xsrf = cookies.get("XSRF-TOKEN");
    if (xsrf != null) {
      request.header("X-XSRF-TOKEN", xsrf.getValue());
    }
    ResultActions result = mvc.perform(request);
    absorb(result.andReturn().getResponse().getCookies());
    return result;
  }

  public ResultActions get(String url, Object... vars) throws Exception {
    return perform(MockMvcRequestBuilders.get(url, vars));
  }

  public ResultActions delete(String url, Object... vars) throws Exception {
    return perform(MockMvcRequestBuilders.delete(url, vars));
  }

  public ResultActions post(String url, String json, Object... vars) throws Exception {
    return perform(
        MockMvcRequestBuilders.post(url, vars).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  public ResultActions put(String url, String json, Object... vars) throws Exception {
    return perform(
        MockMvcRequestBuilders.put(url, vars).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  public ResultActions patch(String url, String json, Object... vars) throws Exception {
    return perform(
        MockMvcRequestBuilders.patch(url, vars).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  public ResultActions login(String username, String password) throws Exception {
    return post(
        "/api/v1/auth/login",
        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
  }

  public String sessionId() {
    Cookie c = cookies.get("SESSION");
    return c == null ? null : c.getValue();
  }

  private void fetchCsrf() throws Exception {
    var res = mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me")).andReturn().getResponse();
    absorb(res.getCookies());
  }

  private void absorb(Cookie[] received) {
    for (Cookie c : received) {
      if (c.getMaxAge() == 0 || c.getValue() == null || c.getValue().isEmpty()) {
        cookies.remove(c.getName());
      } else {
        cookies.put(c.getName(), c);
      }
    }
  }
}
