import unittest
import urllib.error
from unittest import mock

import measure

A_FUNCTION = {
    "FunctionName": "nva-uac-dev-Function-ABC123",
    "LoggingConfig": {"LogGroup": "nva-uac-dev-FunctionLogGroup-XYZ"},
}
ANOTHER_FUNCTION = {"FunctionName": "nva-uac-dev-Other-DEF456"}
LISTED_FUNCTIONS = {"Functions": [A_FUNCTION, ANOTHER_FUNCTION]}
BASE_URL = "https://example.invalid"
PATH = "/organisations/185.90.0.0"
TOKEN = "token"


class PercentileTest(unittest.TestCase):

    def test_should_return_none_for_no_samples(self):
        samples = []

        result = measure.percentile(samples, 0.95)

        self.assertIsNone(result)

    def test_should_pick_the_nearest_rank(self):
        samples = [5.0, 1.0, 3.0, 2.0, 4.0]

        result = measure.percentile(samples, 0.5)

        self.assertEqual(result, 3.0)


class GateTest(unittest.TestCase):

    def test_should_fail_without_samples(self):
        warm = measure.summarise([])

        result = measure.passes(warm, {})

        self.assertFalse(result)

    def test_should_fail_on_an_unexpected_status(self):
        warm = measure.summarise([10.0, 12.0])

        result = measure.passes(warm, {401: 2})

        self.assertFalse(result)

    def test_should_fail_on_connection_errors(self):
        warm = measure.summarise([10.0])

        result = measure.passes(warm, {200: 1, measure.CONNECTION_ERROR_STATUS: 1})

        self.assertFalse(result)

    def test_should_fail_when_the_warm_p95_is_over_budget(self):
        warm = measure.summarise([measure.WARM_P95_BUDGET_MS + 1.0] * 20)

        result = measure.passes(warm, {404: 20})

        self.assertFalse(result)

    def test_should_pass_within_budget_with_expected_statuses_only(self):
        warm = measure.summarise([10.0, 20.0, 30.0])

        result = measure.passes(warm, {200: 2, 404: 1})

        self.assertTrue(result)


class WarmLatenciesTest(unittest.TestCase):

    def test_should_count_connection_failures_as_errors_without_a_latency(self):
        failures = [urllib.error.URLError("unreachable"), TimeoutError()]

        with mock.patch.object(measure.urllib.request, "urlopen", side_effect=failures):
            latencies, statuses = measure.warm_latencies(BASE_URL, PATH, TOKEN, 2)

        self.assertEqual(latencies, [])
        self.assertEqual(statuses, {measure.CONNECTION_ERROR_STATUS: 2})

    def test_should_count_http_errors_by_status(self):
        error = urllib.error.HTTPError(BASE_URL + PATH, 401, "Unauthorized", {}, None)

        with mock.patch.object(measure.urllib.request, "urlopen", side_effect=error):
            latencies, statuses = measure.warm_latencies(BASE_URL, PATH, TOKEN, 3)

        self.assertEqual(len(latencies), 3)
        self.assertEqual(statuses, {401: 3})


class FunctionResolutionTest(unittest.TestCase):

    def test_should_resolve_a_unique_prefix(self):
        with mock.patch.object(measure, "aws", return_value=LISTED_FUNCTIONS):
            function = measure.resolve_function("nva-uac-dev-Function")

        self.assertEqual(function, A_FUNCTION)

    def test_should_refuse_an_ambiguous_prefix(self):
        with mock.patch.object(measure, "aws", return_value=LISTED_FUNCTIONS):
            with self.assertRaises(SystemExit):
                measure.resolve_function("nva-uac-dev")

    def test_should_take_the_log_group_from_the_logging_configuration(self):
        log_group = measure.log_group_of(A_FUNCTION)

        self.assertEqual(log_group, "nva-uac-dev-FunctionLogGroup-XYZ")

    def test_should_fall_back_to_the_default_log_group(self):
        log_group = measure.log_group_of(ANOTHER_FUNCTION)

        self.assertEqual(log_group, "/aws/lambda/nva-uac-dev-Other-DEF456")


class TokenTest(unittest.TestCase):

    def test_should_fail_without_a_token(self):
        arguments = mock.Mock(token=None)

        with mock.patch.dict(measure.os.environ, {}, clear=True):
            with self.assertRaises(SystemExit):
                measure.token_from(arguments)

    def test_should_prefer_the_environment(self):
        arguments = mock.Mock(token="from-argument")

        with mock.patch.dict(measure.os.environ, {measure.TOKEN_VARIABLE: "from-environment"}):
            token = measure.token_from(arguments)

        self.assertEqual(token, "from-environment")


if __name__ == "__main__":
    unittest.main()
