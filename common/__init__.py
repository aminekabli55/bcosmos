"""Shared helpers for the BCosmos Spark jobs: the event contract, the medallion layout and the session.

Nothing in this package talks to Kafka, Postgres or the filesystem at import time: the jobs stay importable from a
pytest run with no cluster and no credentials (spec v2 section 6, rules of section 0).
"""
