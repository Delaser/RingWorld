#!/bin/sh
set -eu

usage() {
    cat <<'EOF'
Usage:
  agent-comms.sh init <agent> [status]
  agent-comms.sh send <from> <to> <message...>
  agent-comms.sh receive <agent>
  agent-comms.sh wait <agent> [seconds]
  agent-comms.sh peek <agent>
  agent-comms.sh history <agent>
  agent-comms.sh heartbeat <agent> [status]
  agent-comms.sh status
  agent-comms.sh where

Agent names may contain letters, numbers, dot, underscore, and hyphen.
The wait timeout defaults to 30 seconds and is capped at 60 seconds.
EOF
}

fail() {
    printf 'agent-comms: %s\n' "$*" >&2
    exit 1
}

validate_agent() {
    value=$1
    case "$value" in
        ''|*[!A-Za-z0-9._-]*) fail "invalid agent name: $value" ;;
    esac
}

timestamp() {
    date -u '+%Y-%m-%dT%H:%M:%SZ'
}

common_git_dir=$(git rev-parse --git-common-dir 2>/dev/null) ||
    fail "run this command inside a RingWorld Git worktree"

case "$common_git_dir" in
    /*) ;;
    *) common_git_dir=$(cd "$common_git_dir" && pwd) ;;
esac

repository_key=$(printf '%s' "$common_git_dir" | cksum | awk '{print $1}')
temporary_root=${TMPDIR:-/tmp}
mailbox_root=${RINGWORLD_AGENT_COMMS_DIR:-"${temporary_root%/}/ringworld-agent-comms-$repository_key"}
inbox_dir="$mailbox_root/inbox"
archive_dir="$mailbox_root/archive"
presence_dir="$mailbox_root/presence"
lock_dir="$mailbox_root/locks"

mkdir -p "$inbox_dir" "$archive_dir" "$presence_dir" "$lock_dir"

acquire_lock() {
    lock_name=$1
    attempts=0
    while ! mkdir "$lock_dir/$lock_name" 2>/dev/null; do
        attempts=$((attempts + 1))
        [ "$attempts" -lt 100 ] ||
            fail "timed out waiting for mailbox lock: $lock_name"
        sleep 0.05
    done
}

release_lock() {
    rmdir "$lock_dir/$1"
}

clean_message() {
    printf '%s' "$*" | tr '\t\r\n' '   '
}

heartbeat() {
    agent=$1
    shift
    validate_agent "$agent"
    state=$(clean_message "${*:-online}")
    temporary="$presence_dir/.$agent.$$"
    printf '%s\t%s\t%s\n' "$(timestamp)" "$agent" "$state" > "$temporary"
    mv "$temporary" "$presence_dir/$agent.status"
}

receive_agent() {
    agent=$1
    validate_agent "$agent"
    inbox="$inbox_dir/$agent.log"
    archive="$archive_dir/$agent.log"
    temporary="$mailbox_root/.receive-$agent-$$"

    acquire_lock "$agent"
    if [ ! -s "$inbox" ]; then
        release_lock "$agent"
        return 1
    fi

    mv "$inbox" "$temporary"
    : > "$inbox"
    cat "$temporary" >> "$archive"
    release_lock "$agent"

    cat "$temporary"
    rm -f "$temporary"
    return 0
}

command=${1:-}
[ -n "$command" ] || {
    usage
    exit 2
}
shift

case "$command" in
    init)
        [ "$#" -ge 1 ] || fail "init requires an agent name"
        agent=$1
        shift
        validate_agent "$agent"
        : > /dev/null
        [ -e "$inbox_dir/$agent.log" ] || : > "$inbox_dir/$agent.log"
        [ -e "$archive_dir/$agent.log" ] || : > "$archive_dir/$agent.log"
        heartbeat "$agent" "${*:-online}"
        printf 'initialized %s at %s\n' "$agent" "$mailbox_root"
        ;;
    send)
        [ "$#" -ge 3 ] || fail "send requires from, to, and a message"
        from=$1
        to=$2
        shift 2
        validate_agent "$from"
        validate_agent "$to"
        message=$(clean_message "$*")
        acquire_lock "$to"
        printf '%s\t%s\t%s\t%s\n' "$(timestamp)" "$from" "$to" "$message" \
            >> "$inbox_dir/$to.log"
        release_lock "$to"
        printf 'sent %s -> %s\n' "$from" "$to"
        ;;
    receive)
        [ "$#" -eq 1 ] || fail "receive requires one agent name"
        receive_agent "$1" || printf 'no pending messages for %s\n' "$1"
        ;;
    wait)
        [ "$#" -ge 1 ] && [ "$#" -le 2 ] ||
            fail "wait requires an agent and optional timeout"
        agent=$1
        validate_agent "$agent"
        timeout=${2:-30}
        case "$timeout" in
            ''|*[!0-9]*) fail "wait timeout must be an integer" ;;
        esac
        [ "$timeout" -le 60 ] || fail "wait timeout cannot exceed 60 seconds"
        elapsed=0
        while [ "$elapsed" -lt "$timeout" ]; do
            if receive_agent "$agent"; then
                exit 0
            fi
            sleep 1
            elapsed=$((elapsed + 1))
        done
        printf 'no message for %s after %s seconds\n' "$agent" "$timeout"
        ;;
    peek)
        [ "$#" -eq 1 ] || fail "peek requires one agent name"
        validate_agent "$1"
        if [ -s "$inbox_dir/$1.log" ]; then
            cat "$inbox_dir/$1.log"
        else
            printf 'no pending messages for %s\n' "$1"
        fi
        ;;
    history)
        [ "$#" -eq 1 ] || fail "history requires one agent name"
        validate_agent "$1"
        if [ -s "$archive_dir/$1.log" ]; then
            cat "$archive_dir/$1.log"
        else
            printf 'no acknowledged message history for %s\n' "$1"
        fi
        ;;
    heartbeat)
        [ "$#" -ge 1 ] || fail "heartbeat requires an agent name"
        agent=$1
        shift
        heartbeat "$agent" "${*:-online}"
        printf 'heartbeat updated for %s\n' "$agent"
        ;;
    status)
        found=false
        for presence in "$presence_dir"/*.status; do
            [ -e "$presence" ] || continue
            cat "$presence"
            found=true
        done
        [ "$found" = true ] || printf 'no agent presence recorded\n'
        ;;
    where)
        printf '%s\n' "$mailbox_root"
        ;;
    help|-h|--help)
        usage
        ;;
    *)
        usage
        fail "unknown command: $command"
        ;;
esac
