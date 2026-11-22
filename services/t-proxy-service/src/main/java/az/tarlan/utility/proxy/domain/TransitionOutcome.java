package az.tarlan.utility.proxy.domain;

/** What to do with an incoming event given the current payment status (docs/events.md, section 5). */
public enum TransitionOutcome {
    /** The status changes to the target status of the event. */
    APPLY,
    /** A late or duplicate event: nothing changes, logged at DEBUG. */
    IGNORE,
    /** The event contradicts the current status: nothing changes, logged at WARN and counted by a metric. */
    CONTRADICTION
}
