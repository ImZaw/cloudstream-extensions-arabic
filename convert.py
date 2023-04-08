import os
import base64
import requests
import json
def main():
    pluginList = newPluginList()
    extensions = getExtensions()
    with open("./plugins.json", "w") as jsonFile:
        jsonFile.write(json.dumps(pluginList))
        jsonFile.close()
    for extension in extensions:
        request = requests.post(f'{os.getenv("data_url")}/upload?password={os.getenv("password")}', files={ 'upload_file': open(extension, "rb") })
    requests.post(f'{os.getenv("data_url")}/upload?password={os.getenv("password")}', files={ 'upload_file': open("plugins.json", "rb") })
def getExtensions():
    filesInDir = filter(lambda x: x.endswith(".cs3"), [f for f in os.listdir('.') if os.path.isfile(f)])
    return filesInDir
def newPluginList():
    with open("./plugins.json") as jsonFile:
        jsonArray = json.load(jsonFile)
        jsonFile.close()
    for i in jsonArray:
        i["url"] = f'{os.getenv("proxy_url")}/view/{i["url"].split("/").pop()}?download'
    return jsonArray
main()
