

<html>
<head><title>Session Content</title></head>
<body>

<a href="xatest"> Get session state</a> (session content - session is not part of XA transaction, two transactional caches, db tables content)
<br/>
<br/>
<h2>Set (no session) attribute</h2>
<form action="xatest" method="POST">
    Key: <input type="TEXT" name="key" />
    <br/>
    Value: <input type="TEXT" name="value" />
    <br/>

    <input type="HIDDEN" name="action" value="setCacheValueOK" />
    <input type="SUBMIT" value="set attribute"/>
</form>

<h2>Set (no session) attribute - throw exception after setting both session and app. cache values</h2>
<form action="xatest" method="POST">
    Key: <input type="TEXT" name="key" />
    <br/>
    Value: <input type="TEXT" name="value" />
    <br/>

    <input type="HIDDEN" name="action" value="setCacheValueERR" />
    <input type="SUBMIT" value="set attribute (expect exception)"/>
</form>

</body>
</html>


