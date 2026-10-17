package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = "app.rate-limit.ip-capacity=5")
class RateLimitIT extends app.gym.support.AbstractIT {

    @Test
    void sixthLoginAttemptFromOneIpIsThrottled() throws Exception {
        int last = 0;
        String retryAfter = null;
        for (int i = 0; i < 6; i++) {
            MvcResult r = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"loginId\":\"nobody" + i + "\",\"password\":\"WrongPass123\"}")).andReturn();
            last = r.getResponse().getStatus();
            retryAfter = r.getResponse().getHeader("Retry-After");
            if (i < 5) {
                assertThat(last).isEqualTo(401);
            }
        }
        assertThat(last).isEqualTo(429);
        assertThat(retryAfter).isNotNull();
    }
}
