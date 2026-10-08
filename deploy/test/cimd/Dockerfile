# The agent's client metadata document, served over https for compose.test.yaml. Test only.
FROM eclipse-temurin:26-jdk
COPY serve.sh CimdServer.java client.json /srv/cimd/
CMD ["bash", "/srv/cimd/serve.sh"]
