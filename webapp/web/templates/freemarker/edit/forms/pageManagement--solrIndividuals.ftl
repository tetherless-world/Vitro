<#-- $This file is distributed under the terms of the license in /doc/license.txt$ -->
<#--This contains the template for the Solr Class individuals content type that is to be cloned and used in page management-->
<#assign classGroup = pageData.classGroup />
<#assign classGroups = pageData.classGroups />
<#assign classes = pageData.classes />
<section id="solrIndividuals" class="contentSectionContainer">
    <label id="variableLabel" for="variable">${i18n().variable_name_all_caps}<span class="requiredHint"> *</span></label>
    <input type="text" name="saveToVar" size="20" value="" id="saveToVar" role="input" />
    <label id="vclassUriLabel" for="vclassUri">${i18n().select_vclass_uri}<span class="requiredHint"> *</span></label>
    <br/>
    <select name="vclassUri" id="vclassUri">
    	<option value="">Select class</option>
    	<#list classes as classObj>
    		<option value="${classObj.URI}">${classObj.name}</option>
    	</#list>
    </select>
    <br/>
    <input  type="button" id="doneWithContent" class="doneWithContent" name="doneWithContent" value="${i18n().save_this_content}" />
    <#if menuAction == "Add">
        <span id="cancelContent"> or <a class="cancel" href="javascript:"  id="cancelContentLink" title="${i18n().cancel_title}">${i18n().cancel_link}</a></span>
    </#if>
</section>
<script>
    var i18nStringsSolrIndividuals = {
        solrIndividuals: '${i18n().solr_individual_results}',
        supplyQueryVariable: '${i18n().supply_query_variable}',
        noApostrophes: '${i18n().apostrophe_not_allowed}',
        noDoubleQuotes: '${i18n().double_quote_note_allowed}',
        supplyQuery: '${i18n().supply_sparql_query}',
        selectClass: '${i18n().select_class_for_solr}'
    };
</script>
${scripts.add('<script type="text/javascript" src="${urls.base}/js/menupage/processSolrDataGetterContent.js"></script>')}
