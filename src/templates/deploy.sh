cd /local/content/caintegrator
mv /local/content/caintegrator/bin /local/content/caintegrator/bin_bak
rm -r /local/content/caintegrator/log
rm -r /local/content/caintegrator/lib
rm -r /local/content/caintegrator/R_source
rm -r /local/content/caintegrator/tempFiles
tar -xvf /local/content/caintegrator/caintegrator-analysis-server.tar
rm -r  /local/content/caintegrator/bin
mv /local/content/caintegrator/bin_bak /local/content/caintegrator/bin
cd /local/content/caintegrator/bin
chmod u+x *.sh
dos2unix *.sh
echo "Deployment complete"
