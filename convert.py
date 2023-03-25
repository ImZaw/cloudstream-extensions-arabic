import os
import base64
import requests
import json
def main():
    pluginList = newPluginList()
    extensions = getExtensionsAsBase64()
    for extension in extensions:
        request = requests.post(f'{os.getenv("data_url")}/{extension["name"]}', data={'data': extension["file"]})
    headers = {'Content-Type': 'application/json', 'Accept':'application/json'}
    requests.post(f'{os.getenv("data_url")}/plugins.json', data=json.dumps({'data': pluginList}), headers=headers)
def getExtensionsAsBase64():
    filesInDir = filter(lambda x: x.endswith(".cs3"), [f for f in os.listdir('.') if os.path.isfile(f)])
    files = []
    for f in filesInDir:
        with open(f"./{f}", "rb") as file:
            bytes = file.read()
            files.append({ "name": f, "file": base64.b64encode(bytes)})
            file.close()
    return files
def newPluginList():
    with open("./plugins.json") as jsonFile:
        jsonArray = json.load(jsonFile)
        jsonFile.close()
    for i in jsonArray:
        i["url"] = f'{os.getenv("proxy_url")}/file/{i["url"].split("/").pop()}'
    return jsonArray
main()