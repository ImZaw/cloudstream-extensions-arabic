var typeTemplate = `<span class="font-light inline-block" style="{{style}}">{{type}}</span>`
var template = `
<div class="plugin flex justify-start flex-col items-center p-4 bg-slate-900 max-h-80 border-black border-2 rounded drop-shadow-md">
    <div class="image">
        <img class="rounded-full h-12 w-12 sm:h-20 sm:w-20 object-cover" src="{{icon_url}}" onerror="this.onerror=null;this.src='https://cdn0.iconfinder.com/data/icons/file-management-system-flat/32/file_managemenr_system_icon_set_flat_style-14-512.png';">
        <svg class="inline relative bottom-6" height="25" width="25">
            <circle cx="12.5" cy="12.5" r="10" fill="{{status}}" />
        </svg>
    </div>
    <div class="font-medium text-center break-word">
        <span><a href="{{url}}">{{name}}</a></span>
        <span class="font-light text-sm block">Language: {{language}}</span>
        <span class="font-light text-xs block">Version: {{version}}</span>
        <span class="font-light text-xs block">Authors: {{authors}}</span>
        {{types}}
        <span class="font-light text-sm block">
            {{description}}
        </span>
    </div>
</div>`
var allList = []
$.getJSON( "https://raw.githubusercontent.com/recloudstream/cs-repos/master/repos-db.json",
function( data ) {
    data.forEach(repoUrl => {
        $.getJSON( repoUrl,
        function( data ) {
            data.pluginLists.forEach(pluginUrl => {
                $.getJSON( pluginUrl,
                function( data ) {
                    data.forEach(element=> allList.push(element))
                })
            })
        })
    })
})
setTimeout(function(){
    $("#title").html(`<span style="display:block;"><b>( ${allList.length} Plugins )</b></span>`)
    allList.forEach(plugin => {
        var statusColor;
        var types = plugin.tvTypes?.map(tvType=> {
            var whatToReturn = typeTemplate.replace("{{type}}", tvType)
            if(tvType == "NSFW") whatToReturn = whatToReturn.replace("{{style}}", "color: red;font-size: 10px;font-weight: bold;")
            else whatToReturn = whatToReturn.replace("{{style}}", "font-size: 10px;")
            return whatToReturn
        })
        if(plugin?.status == 0) statusColor = "red"; else if(plugin?.status == 1) statusColor = "green"; else statusColor = "yellow"
        $(".plugins > #grid").append(
            template
            .replace("{{icon_url}}", plugin.iconUrl?.replace("%size%", "128") ?? "https://cdn0.iconfinder.com/data/icons/file-management-system-flat/32/file_managemenr_system_icon_set_flat_style-14-512.png")
            .replace("{{status}}", statusColor)
            .replace("{{url}}", plugin?.repositoryUrl)
            .replace("{{name}}", plugin?.name)
            .replace("{{language}}", plugin?.language ?? "Not defined")
            .replace("{{authors}}", plugin.authors?.join(", ") || "Not defined")
            .replace("{{version}}", plugin?.version)
            .replace("{{types}}", types?.join("\n") ?? "")
            .replace("{{description}}", plugin?.description ?? "")
            )
    })
}, 1000)
