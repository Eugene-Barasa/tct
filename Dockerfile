FROM tctbot/tctbot:latest
WORKDIR /app
COPY --chmod=777 tctfile /app/tctfile
COPY --chmod=777 tctfile /tctfile
