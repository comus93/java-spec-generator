<%@ taglib prefix="x" uri="http://example.test/legacy" %>
<x:widget data-ref="${user.code}" custom-flag="yes" />
<div onclick="showView()">
  <script>function showView() { return false; }</script>
  <a href="/view?id=${user.code}" onclick="showView()">Open</a>
</div>
