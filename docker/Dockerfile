FROM ubuntu:24.04
LABEL authors="xyzboom"
ARG VERSION
ARG APT_RETRY_TIMES=10

WORKDIR /root
RUN apt-get -o Acquire::Retries=$APT_RETRY_TIMES update  \
    && apt-get -o Acquire::Retries=$APT_RETRY_TIMES install -y wget curl git
RUN wget https://cdn.azul.com/zulu/bin/zulu8.90.0.19-ca-jdk8.0.472-linux_amd64.deb \
    && wget https://cdn.azul.com/zulu/bin/zulu17.62.17-ca-jdk17.0.17-linux_amd64.deb
RUN dpkg -i zulu8.90.0.19-ca-jdk8.0.472-linux_amd64.deb || true \
    && apt-get -o Acquire::Retries=$APT_RETRY_TIMES -f -y install \
    && java -version \
    && rm zulu8.90.0.19-ca-jdk8.0.472-linux_amd64.deb
RUN dpkg -i zulu17.62.17-ca-jdk17.0.17-linux_amd64.deb || true \
    && apt-get -o Acquire::Retries=$APT_RETRY_TIMES -f -y install \
    && java -version \
    && rm zulu17.62.17-ca-jdk17.0.17-linux_amd64.deb

ARG GRADLE_OPTS
ENV GRADLE_OPTS=$GRADLE_OPTS
RUN git clone https://github.com/XYZboom/CrossLangFuzzer && cd CrossLangFuzzer \
    && git checkout $VERSION \
    && mkdir libs && cd libs \
    && wget https://github.com/XYZboom/CrossLangFuzzer/releases/download/dev-ef4368/tree-generator-common.jar
RUN cd CrossLangFuzzer && \
    chmod 777 ./gradlew && \
    ./gradlew :runners:kotlin-runner:build
RUN apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
COPY quick_run.sh .
RUN chmod 777 quick_run.sh
ENTRYPOINT ["bash"]