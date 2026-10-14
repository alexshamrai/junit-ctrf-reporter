package io.github.alexshamrai;

/**
 * A container, such as a test class, that finished without failing. It is kept only so that a failure of the
 * same container in an earlier retry round can be reported as recovered (HP-11).
 *
 * @param uniqueId the container's JUnit unique ID
 * @param start    when the container started, in milliseconds since the epoch
 * @param stop     when it finished
 */
record ContainerSuccess(String uniqueId, long start, long stop) {
}
