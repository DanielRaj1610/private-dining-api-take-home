#!/bin/bash

# Load Test Runner with Cleanup and Report Generation
# Usage: ./run-test.sh <test-name> [k6-options]
# Example: ./run-test.sh quick-benchmark
# Example: ./run-test.sh race-condition-test -e PARTY_SIZE=2

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get test name from argument
TEST_NAME="${1:-quick-benchmark}"
TEST_FILE="$SCRIPT_DIR/${TEST_NAME}.js"
shift || true  # Remove test name from args, remaining args passed to k6

# Validate test file exists
if [ ! -f "$TEST_FILE" ]; then
    echo -e "${RED}Error: Test file not found: $TEST_FILE${NC}"
    echo "Available tests:"
    ls "$SCRIPT_DIR"/*.js | xargs -n1 basename | sed 's/.js$//'
    exit 1
fi

# Create results directory
mkdir -p "$RESULTS_DIR"

# Generate output filenames
RESULT_PREFIX="${RESULTS_DIR}/${TEST_NAME}_${TIMESTAMP}"
LOG_FILE="${RESULT_PREFIX}.log"
JSON_FILE="${RESULT_PREFIX}.json"
SUMMARY_FILE="${RESULT_PREFIX}_summary.txt"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Running Load Test: ${TEST_NAME}${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Test file: $TEST_FILE"
echo "Timestamp: $TIMESTAMP"
echo "Log file: $LOG_FILE"
echo "JSON report: $JSON_FILE"
echo ""

# MongoDB connection details (from docker-compose.yml)
MONGO_URI="mongodb://admin:admin@localhost:27017/private_dining?authSource=admin"
MONGO_CONTAINER="private-dining-mongodb"

# Function to count test reservations before cleanup
count_test_reservations() {
    docker exec "$MONGO_CONTAINER" mongosh "$MONGO_URI" --quiet --eval \
        'db.reservations.countDocuments({ customerName: { $regex: /^(Benchmark|Load Test|Race|Mixed Test|Quick Benchmark)/ } })' 2>/dev/null || echo "0"
}

# Function to cleanup test data
cleanup_test_data() {
    echo ""
    echo -e "${YELLOW}Cleaning up test data...${NC}"

    # Count before cleanup
    local count=$(count_test_reservations)
    echo "Test reservations found: $count"

    # Delete test reservations and slot capacities
    docker exec "$MONGO_CONTAINER" mongosh "$MONGO_URI" --quiet --eval '
        const resCount = db.reservations.deleteMany({
            customerName: { $regex: /^(Benchmark|Load Test|Race|Mixed Test|Quick Benchmark)/ }
        });
        const slotCount = db.slot_capacities.deleteMany({});
        print("Deleted " + resCount.deletedCount + " test reservations");
        print("Deleted " + slotCount.deletedCount + " slot capacity records");
    ' 2>/dev/null || echo -e "${RED}Warning: Could not connect to MongoDB for cleanup${NC}"

    echo -e "${GREEN}Cleanup complete${NC}"
}

# Function to generate summary report
generate_summary() {
    echo ""
    echo -e "${YELLOW}Generating summary report...${NC}"

    {
        echo "=========================================="
        echo "LOAD TEST SUMMARY REPORT"
        echo "=========================================="
        echo ""
        echo "Test Name: $TEST_NAME"
        echo "Timestamp: $TIMESTAMP"
        echo "Test File: $TEST_FILE"
        echo ""
        echo "=========================================="
        echo "KEY METRICS"
        echo "=========================================="

        # Extract key metrics from k6 summary export JSON
        if [ -f "$JSON_FILE" ]; then
            echo ""

            # HTTP Request Duration (k6 summary export format)
            HTTP_P95=$(cat "$JSON_FILE" | jq -r '.metrics.http_req_duration["p(95)"] // "N/A"' 2>/dev/null)
            echo "HTTP Request Duration p95: ${HTTP_P95} ms"

            # Custom metrics (if available) - k6 summary export format
            for metric in reservation_create_ms availability_check_ms report_generate_ms reservation_cancel_ms; do
                val=$(cat "$JSON_FILE" | jq -r ".metrics.${metric}[\"p(95)\"] // empty" 2>/dev/null)
                if [ -n "$val" ]; then
                    echo "${metric} p95: ${val} ms"
                fi
            done

            echo ""

            # Counter metrics - k6 summary export format uses "count" directly
            for counter in reservation_success reservation_failed capacity_exceeded bookings_success bookings_capacity_exceeded bookings_other_error; do
                val=$(cat "$JSON_FILE" | jq -r ".metrics.${counter}.count // empty" 2>/dev/null)
                if [ -n "$val" ]; then
                    echo "${counter}: ${val}"
                fi
            done

            # Request counts
            TOTAL_REQS=$(cat "$JSON_FILE" | jq -r '.metrics.http_reqs.count // "N/A"' 2>/dev/null)
            FAILED_RATE=$(cat "$JSON_FILE" | jq -r '.metrics.http_req_failed.rate // "N/A"' 2>/dev/null)
            echo ""
            echo "Total HTTP Requests: ${TOTAL_REQS}"
            echo "Failed Request Rate: ${FAILED_RATE}"

            echo ""
            echo "=========================================="
            echo "THRESHOLD RESULTS"
            echo "=========================================="

            # Parse thresholds
            cat "$JSON_FILE" | jq -r '
                .root_group.checks // {} | to_entries[] | "  \(.key): \(if .value.passes > 0 and .value.fails == 0 then "✓ PASS" else "✗ FAIL" end) (\(.value.passes)/\(.value.passes + .value.fails))"
            ' 2>/dev/null || true

            # Also show metric thresholds
            cat "$JSON_FILE" | jq -r '
                if .metrics then
                    .metrics | to_entries[] | select(.value.thresholds != null) |
                    .value.thresholds | to_entries[] |
                    "  \(.key): \(if .value.ok then "✓ PASS" else "✗ FAIL" end)"
                else empty end
            ' 2>/dev/null || true
        else
            echo "JSON results not available"
        fi

        echo ""
        echo "=========================================="
        echo "FILES GENERATED"
        echo "=========================================="
        echo "Log: $LOG_FILE"
        echo "JSON: $JSON_FILE"
        echo "Summary: $SUMMARY_FILE"
        echo ""
        echo "=========================================="
    } > "$SUMMARY_FILE"

    echo -e "${GREEN}Summary saved to: $SUMMARY_FILE${NC}"
}

# Run the test
echo -e "${GREEN}Starting k6 test...${NC}"
echo ""

# Run k6 and capture output
# Note: k6 summary JSON is generated by handleSummary in each test file
# The --out json flag creates a different streaming format, so we don't use it
k6 run "$TEST_FILE" \
    --summary-export="$JSON_FILE" \
    "$@" 2>&1 | tee "$LOG_FILE"

TEST_EXIT_CODE=${PIPESTATUS[0]}

# Generate summary report
generate_summary

# Show summary
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}TEST COMPLETE${NC}"
echo -e "${GREEN}========================================${NC}"
cat "$SUMMARY_FILE"

# Cleanup test data
cleanup_test_data

# Exit with test exit code
if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo ""
    echo -e "${GREEN}All thresholds passed!${NC}"
else
    echo ""
    echo -e "${YELLOW}Test completed with exit code: $TEST_EXIT_CODE${NC}"
    echo "Note: Exit code 99 indicates threshold failures (expected for race condition tests)"
fi

exit $TEST_EXIT_CODE
