# The image the Spark test suite runs in.
#
# Why an image at all: Spark does not start on Windows without `winutils.exe`/`HADOOP_HOME`, so a local `pytest`
# cannot open a session on the development machine, and ADR 0017 already says Spark runs in Docker. This image
# derives from the same tag the jobs run with (infrastructure/docker-compose.yml), so tests and cluster use one Spark.
#
# Two details that cost real time, hence the comments:
#  * the image runs as uid 185 `spark`, whose HOME is `/nonexistent`, so neither `/opt` nor a user site is writable:
#    the install runs as root and the image switches back afterwards;
#  * plain `python3` in this image cannot import pyspark — the package lives in `/opt/spark/python/lib/pyspark.zip`
#    and only the image's own entrypoint puts that on the path. Because this image replaces the entrypoint with
#    pytest, a `.pth` file records the zips once, globbed from the image, instead of hardcoding a version that the
#    next image tag would break.
FROM apache/spark:4.2.0-python3

# Keep the pin in step with requirements.txt and with the image tag in infrastructure/docker-compose.yml.
ARG PYTEST_VERSION=9.1.1

USER root
RUN python3 -m pip install --no-cache-dir --target /opt/spark-test-lib "pytest==${PYTEST_VERSION}" \
    && SITE_PACKAGES="$(python3 -c 'import site; print(site.getsitepackages()[0])')" \
    && printf '%s\n' /opt/spark-test-lib $(ls -d /opt/spark/python/lib/*.zip | sort) > "${SITE_PACKAGES}/bcosmos-spark.pth" \
    && cat "${SITE_PACKAGES}/bcosmos-spark.pth" \
    && python3 -c "import pytest, pyspark; print('pytest', pytest.__version__, '/ pyspark', pyspark.__version__)"

# Spark needs an IP it can advertise; loopback is what a local-mode test session wants.
ENV SPARK_LOCAL_IP=127.0.0.1
# The workspace is bind-mounted: never leave bytecode or a pytest cache in it from inside the container.
ENV PYTHONDONTWRITEBYTECODE=1

USER 185

WORKDIR /opt/jobs

ENTRYPOINT ["python3", "-m", "pytest"]
CMD ["-q", "-p", "no:cacheprovider", "tests"]
