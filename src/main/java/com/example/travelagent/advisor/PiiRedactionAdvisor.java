package com.example.travelagent.advisor;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.Ordered;

/**
 * Masks emails, payment card numbers and phone numbers in the user's message before it
 * leaves the application. Runs before moderation and chat memory, so the raw values are
 * never sent to OpenAI or stored in conversation history.
 */
public class PiiRedactionAdvisor implements BaseAdvisor {

	/** Context key holding how many values were redacted from the current message. */
	public static final String REDACTION_COUNT = "travel_agent.pii_redactions";

	static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 50;

	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	// 13-19 digits, optionally grouped with spaces or dashes; confirmed with a Luhn check
	private static final Pattern CARD = Pattern.compile("(?<!\\d)\\d(?:[ -]?\\d){12,18}(?!\\d)");

	// optional + or ( prefix, then digits with common separators; 10-15 digits in total
	private static final Pattern PHONE = Pattern.compile("(?<![\\w+(])[+(]?\\d[\\d ().-]{8,}\\d(?!\\d)");

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
		UserMessage userMessage = request.prompt().getUserMessage();
		String text = (userMessage != null) ? userMessage.getText() : null;
		if (text == null || text.isEmpty()) {
			return request;
		}
		Redaction redaction = redact(text);
		if (redaction.count() == 0) {
			return request;
		}
		return request.mutate()
			.prompt(request.prompt().augmentUserMessage((message) -> message.mutate().text(redaction.text()).build()))
			.context(REDACTION_COUNT, redaction.count())
			.build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
		return response;
	}

	static Redaction redact(String text) {
		int[] count = { 0 };
		String result = replace(EMAIL, text, (match) -> {
			count[0]++;
			return "[EMAIL]";
		});
		result = replace(CARD, result, (match) -> {
			if (!passesLuhn(digitsOf(match))) {
				return match;
			}
			count[0]++;
			return "[CARD]";
		});
		result = replace(PHONE, result, (match) -> {
			int digits = digitsOf(match).length();
			if (digits < 10 || digits > 15) {
				return match;
			}
			count[0]++;
			return "[PHONE]";
		});
		return new Redaction(result, count[0]);
	}

	private static String replace(Pattern pattern, String text, UnaryOperator<String> replacer) {
		Matcher matcher = pattern.matcher(text);
		return matcher.replaceAll((match) -> Matcher.quoteReplacement(replacer.apply(match.group())));
	}

	private static String digitsOf(String value) {
		return value.replaceAll("\\D", "");
	}

	private static boolean passesLuhn(String digits) {
		int sum = 0;
		boolean doubleIt = false;
		for (int i = digits.length() - 1; i >= 0; i--) {
			int d = digits.charAt(i) - '0';
			if (doubleIt) {
				d *= 2;
				if (d > 9) {
					d -= 9;
				}
			}
			sum += d;
			doubleIt = !doubleIt;
		}
		return sum % 10 == 0;
	}

	@Override
	public String getName() {
		return "PiiRedactionAdvisor";
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	record Redaction(String text, int count) {
	}

}
